package org.eclipse.jdt.internal.debug.ui;

/**********************************************************************
Copyright (c) 2002 IBM Corp. and others.
All rights reserved. This program and the accompanying materials
are made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html

Contributors:
    IBM Corporation - Initial implementation
**********************************************************************/
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchesListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.Message;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import com.sun.jdi.InvocationException;

public class JavaDetailFormattersManager implements IPropertyChangeListener, IDebugEventSetListener, ILaunchesListener {
	/**
	 * The default detail formatters manager.	 */
	static private JavaDetailFormattersManager fgDefault;
	
	/**
	 * Return the default detail formatters manager.
	 * 
	 * @return default detail formatters manager.	 */
	static public JavaDetailFormattersManager getDefault() {
		if (fgDefault == null) {
			fgDefault= new JavaDetailFormattersManager();
		}
		return fgDefault;
	}

	/**
	 * Map of types to the associated formatter (code snippet).
	 * (<code>String</code> -> <code>String</code>)
	 */
	private HashMap fDetailFormattersMap;
	
	/**
	 * Cache of compiled expressions.
	 * Associate a pair type name/debug target to a compiled expression.	 */
	private HashMap fCacheMap;
	
	private IJavaValue fToStringValue;
	
	/**
	 * JavaDetailFormattersManager constructor.	 */
	private JavaDetailFormattersManager() {
		populateDetailFormattersMap();
		JDIDebugUIPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(this);
		DebugPlugin.getDefault().getLaunchManager().addLaunchListener(this);
		DebugPlugin.getDefault().addDebugEventListener(this);
		fCacheMap= new HashMap();
	}
	
	/**
	 * Populate the detail formatters map with data from preferences.
	 */
	private void populateDetailFormattersMap() {
		String[] detailFormattersList= JavaDebugOptionsManager.parseList(JDIDebugUIPlugin.getDefault().getPreferenceStore().getString(IJDIPreferencesConstants.PREF_DETAIL_FORMATTERS_LIST));
		fDetailFormattersMap= new HashMap(detailFormattersList.length / 3);
		for (int i= 0, length= detailFormattersList.length; i < length;) {
			String typeName= detailFormattersList[i++];
			String snippet= detailFormattersList[i++].replace('\u0000', ',');
			boolean enabled= ! JavaDetailFormattersPreferencePage.DETAIL_FORMATTER_IS_DISABLED.equals(detailFormattersList[i++]);
			fDetailFormattersMap.put(typeName, new DetailFormatter(typeName, snippet, enabled));
		}
	}
	
	/**
	 * Apply on the given object the formatter defined for its type.
	 * If no formatter is find for the type of the given object,
	 * return <code>null</code>.
	 * 	 * @param objectValue the object to	 * @param thread thread in which the code snippet will be executed. 	 * @return a string which represent the object	 * @throws DebugException	 */
	public IJavaValue getValueDetail(IJavaObject objectValue, final IJavaThread thread, IJavaProject project) throws DebugException {
		if (project == null) {
			return null;
		}
		// get the evaluation engine
		JDIDebugTarget debugTarget= (JDIDebugTarget) thread.getDebugTarget();
		IAstEvaluationEngine evaluationEngine= JDIDebugUIPlugin.getDefault().getEvaluationEngine(project, debugTarget);
		// get the compiled expression to use
		ICompiledExpression compiledExpression= getCompiledExpression(objectValue, debugTarget, evaluationEngine);
		if (compiledExpression == null) {
			return null;
		}
		IEvaluationListener listener= new IEvaluationListener() {
			public void evaluationComplete(IEvaluationResult result) {
				if (!result.hasErrors()) {
					fToStringValue= result.getValue();
				} else {
					StringBuffer error= new StringBuffer(DebugUIMessages.getString("JavaDetailFormattersManager.Detail_formatter_error___1")); //$NON-NLS-1$
					DebugException exception= result.getException();
					if (exception != null) {
						Throwable throwable= exception.getStatus().getException();
						error.append("\n\t\t"); //$NON-NLS-1$
						if (throwable instanceof InvocationException) {
							error.append(MessageFormat.format(DebugUIMessages.getString("JavaDetailFormattersManager.An_exception_occurred__{0}_3"), new String[] {((InvocationException) throwable).exception().referenceType().name()})); //$NON-NLS-1$
						} else {
							error.append(exception.getStatus().getMessage());
						}
					} else {
						Message[] errors= result.getErrors();
						for (int i= 0, length= errors.length; i < length; i++) {
							error.append("\n\t\t").append(errors[i].getMessage()); //$NON-NLS-1$
						}
					}
					fToStringValue= ((IJavaDebugTarget)thread.getDebugTarget()).newValue(error.toString());
				}
				synchronized(this) {
					notify();
				}
			}
		};
		synchronized(listener) {
			// evaluate
			evaluationEngine.evaluateExpression(compiledExpression, objectValue, thread, listener, DebugEvent.EVALUATION_IMPLICIT, false);
			// wait until the evaluation completion
			try {
				listener.wait();
			} catch (InterruptedException e) {
			}
		}
		return fToStringValue;
	}
	
	public boolean hasAssociatedDetailFormatter(IJavaType type) {
		return getAssociatedDetailFormatter(type) != null;
	}
	
	public DetailFormatter getAssociatedDetailFormatter(IJavaType type) {
		String typeName;
		try {
			while (type instanceof IJavaArrayType) {
				type= ((IJavaArrayType)type).getComponentType();
			}
			if (type instanceof IJavaClassType) {
				typeName= type.getName();
			} else {
				return null;
			}
		} catch (DebugException e) {
			return null;
		}
		return (DetailFormatter)fDetailFormattersMap.get(typeName);
	}
	
	public void setAssociatedDetailFormatter(DetailFormatter detailFormatter) {
		fDetailFormattersMap.put(detailFormatter.getTypeName(), detailFormatter);
		savePreference();
	}


	private void savePreference() {
		Collection valuesList= fDetailFormattersMap.values();
		String[] values= new String[valuesList.size() * 3];
		int i= 0;
		for (Iterator iter= valuesList.iterator(); iter.hasNext();) {
			DetailFormatter detailFormatter= (DetailFormatter) iter.next();
			values[i++]= detailFormatter.getTypeName();
			values[i++]= detailFormatter.getSnippet().replace(',','\u0000');
			values[i++]= detailFormatter.isEnabled() ? JavaDetailFormattersPreferencePage.DETAIL_FORMATTER_IS_ENABLED : JavaDetailFormattersPreferencePage.DETAIL_FORMATTER_IS_DISABLED;
		}
		String pref = JavaDebugOptionsManager.serializeList(values);
		JDIDebugUIPlugin.getDefault().getPreferenceStore().setValue(IJDIPreferencesConstants.PREF_DETAIL_FORMATTERS_LIST, pref);
	}
	/**
	 * Return the detail formatter (code snippet) associate with
	 * the given type or one of its super type.
	 */
	private String getDetailFormatter(IJavaClassType type) throws DebugException {
		if (type == null) {
			return null;
		}
		String typeName= type.getName();
		if (fDetailFormattersMap.containsKey(typeName)) {
			DetailFormatter detailFormatter= (DetailFormatter)fDetailFormattersMap.get(typeName);
			if (detailFormatter.isEnabled()) {
				return detailFormatter.getSnippet();
			}
		}
		return getDetailFormatter(type.getSuperclass());
	}

	/**
	 * Return the compiled expression which corresponds to the code formatter associated
	 * with the type of the given object.
	 * The code snippet is compiled in the context of the given object.
	 */
	private ICompiledExpression getCompiledExpression(IJavaObject javaObject, JDIDebugTarget debugTarget, IAstEvaluationEngine evaluationEngine) throws DebugException {
		IJavaClassType type= (IJavaClassType)javaObject.getJavaType();
		String typeName= type.getName();
		Key key= new Key(typeName, debugTarget);
		if (fCacheMap.containsKey(key)) {
			return (ICompiledExpression) fCacheMap.get(key);
		} else {
			String snippet= getDetailFormatter(type);
			if (snippet != null) {
				ICompiledExpression res= evaluationEngine.getCompiledExpression(snippet, javaObject);
				fCacheMap.put(key, res);
				return res;
			}
		}
		return null;
	}

	/**
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(PropertyChangeEvent)	 */
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty().equals(IJDIPreferencesConstants.PREF_DETAIL_FORMATTERS_LIST)) {
			populateDetailFormattersMap();
			fCacheMap.clear();
		}
	}
	/**
	 * @see org.eclipse.debug.core.IDebugEventSetListener#handleDebugEvents(DebugEvent[])
	 */
	public void handleDebugEvents(DebugEvent[] events) {
		for (int i = 0; i < events.length; i++) {
			DebugEvent event = events[i];
			if (event.getSource() instanceof JDIDebugTarget && event.getKind() == DebugEvent.TERMINATE) {
				deleteCacheForTarget((JDIDebugTarget) event.getSource());
			}	
		}
	}

	/**
	 * @see org.eclipse.debug.core.ILaunchesListener#launchesAdded(ILaunch[])
	 */
	public void launchesAdded(ILaunch[] launches) {
	}

	/**
	 * @see org.eclipse.debug.core.ILaunchesListener#launchesChanged(ILaunch[])
	 */
	public void launchesChanged(ILaunch[] launches) {
	}

	/**
	 * @see org.eclipse.debug.core.ILaunchesListener#launchesRemoved(ILaunch[])
	 */
	public void launchesRemoved(ILaunch[] launches) {
		for (int i = 0; i < launches.length; i++) {
			ILaunch launch = launches[i];
			IDebugTarget[] debugTargets= launch.getDebugTargets();
			for (int j = 0; j < debugTargets.length; j++) {
				if (debugTargets[j] instanceof JDIDebugTarget) {
					deleteCacheForTarget((JDIDebugTarget)debugTargets[j]);		
				}
			}
		}
	}

	/**
	 * Remove from the cache compiled expression associated with
	 * the given debug target.
	 * 
	 * @param debugTarget 
	 */
	private synchronized void deleteCacheForTarget(JDIDebugTarget debugTarget) {
		for (Iterator iter= fCacheMap.keySet().iterator(); iter.hasNext();) {
			Key key= (Key) iter.next();
			if ((key).fDebugTarget == debugTarget) {
				iter.remove();
			}
		}
	}

	/**
	 * Object used as the key in the cache map for associate a compiled
	 * expression with a pair type name/debug target
	 */
	static private class Key {
		private String fTypeName;
		private JDIDebugTarget fDebugTarget;
		
		Key(String typeName, JDIDebugTarget debugTarget) {
			fTypeName= typeName;
			fDebugTarget= debugTarget;
		}
		
		public boolean equals(Object obj) {
			if (obj instanceof Key) {
				Key key= (Key) obj;
				return fTypeName != null && fDebugTarget != null && fTypeName.equals(key.fTypeName) && fDebugTarget.equals(key.fDebugTarget);
			} else {
				return false;
			}
		}

		public int hashCode() {
			return fTypeName.hashCode() / 2 + fDebugTarget.hashCode() / 2;
		}
	}

}

package org.eclipse.jdt.debug.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import org.eclipse.debug.core.DebugException;

/**
 * Modifiers common to Java debug elements that have  associated Java
 * member declarations. For example, the method associated with a stack frame,
 * or the field associated with a variable.
 * <p>
 * Clients are not intended to implement this interface.
 * </p>
 */
public interface IJavaModifiers {		

	/**
	 * Returns whether the associated Java construct is declared as public.
	 *
	 * @return whether the associated Java construct is declared as public
	 * @exception DebugException if this method fails.  Reasons include:
	 * <ul><li>Failure communicating with the VM.  The DebugException's
	 * status code contains the underlying exception responsible for
	 * the failure.</li></ul>
	 */
	public boolean isPublic() throws DebugException;
	/**
	 * Returns whether the associated Java construct is declared as private.
	 *
	 * @return whether the associated Java construct is declared as private
	 * @exception DebugException if this method fails.  Reasons include:
	 * <ul><li>Failure communicating with the VM.  The DebugException's
	 * status code contains the underlying exception responsible for
	 * the failure.</li></ul>
	 */
	public boolean isPrivate() throws DebugException;
	/**
	 * Returns whether the associated Java construct is declared as protected.
	 *
	 * @return whether the associated Java construct is declared as protected
	 * @exception DebugException if this method fails.  Reasons include:
	 * <ul><li>Failure communicating with the VM.  The DebugException's
	 * status code contains the underlying exception responsible for
	 * the failure.</li></ul>
	 */
	public boolean isProtected() throws DebugException;
	/**
	 * Returns whether the associated Java construct is declared with
	 * no protection modifier (i.e. package private protection).
	 *
	 * @return whether the associated Java construct is declared as package private
	 * @exception DebugException if this method fails.  Reasons include:
	 * <ul><li>Failure communicating with the VM.  The DebugException's
	 * status code contains the underlying exception responsible for
	 * the failure.</li></ul>
	 */
	public boolean isPackagePrivate() throws DebugException;
	/**
	 * Returns whether the associated Java construct is declared as final.
	 * 
	 * @return whether the associated Java construct is declared as final
	 * @exception DebugException if this method fails.  Reasons include:
	 * <ul><li>Failure communicating with the VM.  The DebugException's
	 * status code contains the underlying exception responsible for
	 * the failure.</li></ul>
	 */
	public boolean isFinal() throws DebugException;
	/**
	 * Returns whether the associated Java construct is declared as static.
	 * 
	 * @return whether the associated Java construct is declared as static
	 * @exception DebugException if this method fails.  Reasons include:
	 * <ul><li>Failure communicating with the VM.  The DebugException's
	 * status code contains the underlying exception responsible for
	 * the failure.</li></ul>
	 */
	public boolean isStatic() throws DebugException;
	/**
	 * Returns whether the associated Java construct is synthetic. 
	 * Synthetic members are generated by the compiler
	 * and are not present in source code.
	 *
	 * @return whether the associated Java construct is synthetic
	 * @exception DebugException if this method fails.  Reasons include:
	 * <ul><li>Failure communicating with the VM.  The DebugException's
	 * status code contains the underlying exception responsible for
	 * the failure.</li></ul>
	 */
	public boolean isSynthetic() throws DebugException;


}



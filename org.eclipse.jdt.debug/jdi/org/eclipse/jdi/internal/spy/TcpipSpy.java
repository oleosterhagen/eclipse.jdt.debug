package org.eclipse.jdi.internal.spy;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;

/**
 * This class can be used to spy all JDWP packets. It should be configured 'in between' the debugger
 * application and the VM (or J9 debug proxy).
 * Its parameters are:
 *  1) The port number to which the debugger application connects;
 *  2) The port number on which the VM or proxy waits for a JDWP connection;
 *  3) The file where the trace is written to.
 *
 * Note that if this program is used for tracing JDWP activity of Leapfrog, the
 * 'debug remote program' option must be used, and the J9 proxy must first be started up by hand
 * on the port to which Leapfrog will connect.
 * The J9 proxy that is started up by Leapfrog is not used and will return immediately.
 */ 
public class TcpipSpy extends Thread {
		
	private static final byte[] handshakeBytes = "JDWP-Handshake".getBytes(); //$NON-NLS-1$
	private String fDescription;
	private DataInputStream fDataIn;
	private DataOutputStream fDataOut;
	private static PrintStream out = System.out;
	
	public TcpipSpy(String description, InputStream in, OutputStream out) throws IOException {
		fDescription = description;
		fDataIn = new DataInputStream(new BufferedInputStream(in));
		fDataOut = new DataOutputStream(new BufferedOutputStream(out));
	}

	public void run() {
		try {
			// Skip handshake.
			int handshakeLength;
			
			handshakeLength = handshakeBytes.length;
			while (handshakeLength-- > 0) {
				int b = fDataIn.read();
				fDataOut.write(b);
			}
			out.println(MessageFormat.format(SpyMessages.getString("TcpipSpy.{0}_performed_handshake_1"), new String[]{fDescription})); //$NON-NLS-1$
			out.flush();
			fDataOut.flush();
			
			// Print all packages.
			while (true) {
				int length = fDataIn.readInt();
				fDataOut.writeInt(length);
				length -= 4;
				out.println();
				out.println(MessageFormat.format(SpyMessages.getString("TcpipSpy.{0}_remaining_length__{1}_2"), new String[]{fDescription, Integer.toString(length)})); //$NON-NLS-1$
				while (length-- > 0) {
					int b = fDataIn.readUnsignedByte();
					fDataOut.write(b);
					if (b <= 0xf)
						out.print(" 0"); //$NON-NLS-1$
					else
						out.print(" "); //$NON-NLS-1$

					out.print(Integer.toHexString(b));
				}
				out.flush();
				fDataOut.flush();
			}
		} catch (Exception e) {
			out.println(MessageFormat.format(SpyMessages.getString("TcpipSpy.{0}_ERROR__{1}_5"), new String[]{fDescription, e.toString()})); //$NON-NLS-1$
		}
	}

	public static void main(String[] args) {
		int inPort = 0;
		int outPort = 0;
		String outputFile = null;
		try {
			inPort = Integer.parseInt(args[0]);
			outPort = Integer.parseInt(args[1]);
			outputFile = args[2];
		}
		catch (Exception e) {
			out.println(SpyMessages.getString("TcpipSpy.Usage__TcpipSpy_<inPort>_<outPort>_<outputFile>_6")); //$NON-NLS-1$
			System.exit(-1);
		}
		
		try {
			File file = new File(outputFile);
			out.println(SpyMessages.getString("TcpipSpy.TcpipSpy__logging_output_to__7") + file.getAbsolutePath()); //$NON-NLS-1$
			out = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)));
		}
		catch (Exception e) {
			out.println(SpyMessages.getString("TcpipSpy.Cannot_open__8") + outputFile); //$NON-NLS-1$
			System.exit(-2);
		}
		out.println(MessageFormat.format(SpyMessages.getString("TcpipSpy.Waiting_in_port_{0},_connecting_to_port_{1}_9"), new String[]{Integer.toString(inPort), Integer.toString(outPort)})); //$NON-NLS-1$
		out.println();
		try {
			ServerSocket serverSock = new ServerSocket(inPort);
			Socket inSock = serverSock.accept();
			Socket outSock = new Socket(InetAddress.getLocalHost(), outPort);
			new TcpipSpy(SpyMessages.getString("TcpipSpy.From_debugger__10"), inSock.getInputStream(), outSock.getOutputStream()).start(); //$NON-NLS-1$
			new TcpipSpy(SpyMessages.getString("TcpipSpy.From_VM__11"), outSock.getInputStream(), inSock.getOutputStream()).start(); //$NON-NLS-1$
		} catch (Exception e) {
			out.println(e);
		}
	}
}
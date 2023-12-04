package nachos.vm;

import java.util.ArrayList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		IPT = new ArrayList<>();
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	protected class invertedPageTableEntry {
		VMProcess process;
		TranslationEntry TE;

		public invertedPageTableEntry(VMProcess process, TranslationEntry TE) {
			this.process = process;
			this.TE = TE;
		}
	}

	public ArrayList<invertedPageTableEntry> IPT;

	public VMProcess getProcess(int spn) {
		return IPT.get(spn).process;
	}
}

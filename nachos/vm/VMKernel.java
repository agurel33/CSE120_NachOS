package nachos.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

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

	public static LinkedList<Integer> slinky = null;

	public static int slinky_size = 0;

	public static VMKernel VMkernel = null;
	public static StubFileSystem fs = null; 
	public static OpenFile swap = null; 
	public static HashMap<Integer, Integer> swapTable = null;
	public static HashMap<Integer, Boolean> faultTable = null;

	public VMKernel() {
		//super();
		if(VMkernel == null) {
			VMkernel = this;
		}
		if(slinky == null) {
			slinky = new LinkedList<>();
			for(int i = 0; i < 16; i++) {
				slinky.add(i);
				slinky_size++;
			}
		}
		if(swapTable == null) {
			swapTable = new HashMap<>();
		}
		if(faultTable == null) {
			faultTable = new HashMap<>();
		}
		if(fs == null) {
			fs = (StubFileSystem) ThreadedKernel.fileSystem;
			System.out.println(fs.toString());
		}
		if(swap == null) {
			swap = fs.open("swap", true);
		}
	}

	public static int getSPN(){
		boolean status = Machine.interrupt().disable();
		locky.acquire();
		if(slinky.size() == 0) {
			int curr_size = slinky_size;
			for(int i = curr_size; i < curr_size + 16; i++) {
				slinky.add(i);
				slinky_size++;
			}
		}
		int output = slinky.pop();
		locky.release();
		Machine.interrupt().restore(status);
		return output;
	}

	public static void releaseSPN(int spn){
		boolean status = Machine.interrupt().disable();
		locky.acquire();
		slinky.push(spn);
		locky.release();
		Machine.interrupt().restore(status);
	}

	public void cleanSwap() {
		//
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
		boolean faulted;

		public invertedPageTableEntry(VMProcess process, TranslationEntry TE) {
			this.process = process;
			this.TE = TE;
			faulted = true;
		}
	}

	public ArrayList<invertedPageTableEntry> IPT;

	public VMProcess getProcess(int spn) {
		return IPT.get(spn).process;
	}

	public void newEntry(VMProcess process, TranslationEntry TE) {
		invertedPageTableEntry curr = new invertedPageTableEntry(process, TE);
		int index = TE.ppn;
		IPT.add(index,curr);
	}
}

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
	public static LinkedList<Integer> linky = null;

	public static int slinky_size = 0;

	public static VMKernel VMkernel = null;
	public static StubFileSystem fs = null; 
	public static OpenFile swap = null; 
	public static HashMap<Integer, Integer> swapTable = null;
	public static ArrayList<Integer> seenTable = null;
	public static FileSystem fileSystem = null;


	public VMKernel() {
		String schedulerName = Config.getString("ThreadedKernel.scheduler");
		scheduler = (Scheduler) Lib.constructObject(schedulerName);

		// set fileSystem
		String fileSystemName = Config.getString("ThreadedKernel.fileSystem");
		if (fileSystemName != null)
			fileSystem = (FileSystem) Lib.constructObject(fileSystemName);
		else if (Machine.stubFileSystem() != null)
			fileSystem = Machine.stubFileSystem();
		else
			fileSystem = null;

		// start threading
		new KThread(null);

		alarm = new Alarm();

		Machine.interrupt().enable();
		//super();
		if(VMkernel == null) {
			VMkernel = this;
		}
		if(linky == null) {
			linky = new LinkedList<>();
			int numForLinky = Machine.processor().getNumPhysPages();
			for (int i = 0; i < numForLinky; i++){
				linky.add(i);
			}
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
		if(seenTable == null) {
			seenTable = new ArrayList<>();
		}
		if(fs == null) {
			fs = (StubFileSystem) fileSystem;
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

	public static int getNextOpenPage(){
		//boolean status = Machine.interrupt().disable();
		locky.acquire();
		int output = linky.pop();
		locky.release();
		//Machine.interrupt().restore(status);
		return output;
	}

	public static void releasePage(int pageAddy){
		boolean status = Machine.interrupt().disable();
		locky.acquire();
		linky.push(pageAddy);
		locky.release();
		Machine.interrupt().restore(status);
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
		//super.run();
		VMProcess process = (VMProcess) VMProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		if (!process.execute(shellProgram, new String[] {})) {
		    System.out.println ("Could not find executable '" +
					shellProgram + "', trying '" +
					shellProgram + ".coff' instead.");
		    shellProgram += ".coff";
		    if (!process.execute(shellProgram, new String[] {})) {
			System.out.println ("Also could not find '" +
					    shellProgram + "', aborting.");
			Lib.assertTrue(false);
		    }

		}
		KThread.currentThread().finish();
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
		System.out.println("Adding (vpn,ppn): " + TE.ppn + " " + TE.vpn);
		invertedPageTableEntry curr = new invertedPageTableEntry(process, TE);
		int index = TE.ppn;
		if(vpnFromPpn(TE.ppn) == -1) {
			IPT.add(curr);
		}
		else {
			IPT.set(index,curr);
		}
	}

	public int vpnFromPpn(int ppn) {
		for(invertedPageTableEntry ent: IPT) {
		System.out.println("ppn we want: " + ppn + ", ppn we are looking at: " + ent.TE.ppn);
			if(ent.TE.ppn == ppn) {
				return ent.TE.vpn;
			}
		}
		return -1;
	}
}

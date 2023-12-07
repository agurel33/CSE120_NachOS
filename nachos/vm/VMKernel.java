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
	//public static ArrayList<Integer> seenTable = null;
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
			for(int i = 0; i < Machine.processor().getNumPhysPages(); i++) {
				slinky.add(i);
				slinky_size++;
			}
		}
		if(fs == null) {
			fs = (StubFileSystem) fileSystem;
		}
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
		//boolean status = Machine.interrupt().disable();
		locky.acquire();
		linky.push(pageAddy);
		locky.release();
		//Machine.interrupt().restore(status);
	}

	public static int getSPN(){
		//boolean status = Machine.interrupt().disable();
		locky.acquire();
		if(slinky.size() == 0) {
			int curr_size = slinky_size;
			for(int i = 0; i < Machine.processor().getNumPhysPages(); i++) {
				slinky.add(curr_size + i);
				slinky_size++;
			}
		}
		int output = slinky.pop();
		locky.release();
		//Machine.interrupt().restore(status);
		return output;
	}

	// public static void releaseSPN(int spn){
	// 	//boolean status = Machine.interrupt().disable();
	// 	locky.acquire();
	// 	slinky.push(spn);
	// 	locky.release();
	// 	//Machine.interrupt().restore(status);
	// }

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		IPT = new ArrayList<>();
		if(swap == null) {
			swap = fs.open("swap", true);
		}
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
		Lib.debug(dbgVM, "entering terminate");
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

	public static ArrayList<invertedPageTableEntry> IPT;

	public VMProcess getProcess(int ppn) {
		for(invertedPageTableEntry item: IPT) {
			if(item != null && item.TE.ppn == ppn) {
				return item.process;
			}
		}
		return null;
	}

	public void newEntry(VMProcess process, TranslationEntry TE) {
		//System.out.println("Adding (vpn,ppn): " + TE.vpn + ", " + TE.ppn);
		for(invertedPageTableEntry item: IPT) {
			if(item != null && item.TE.ppn == TE.ppn) {
				item.TE = TE;
				item.process = process;
				return;
			}
		}
		invertedPageTableEntry curr = new invertedPageTableEntry(process, TE);
		IPT.add(curr);
	}

	public int vpnFromPpn(int ppn) {
		for(invertedPageTableEntry item: IPT) {
			if(item != null && item.TE.ppn == ppn) {
				//System.out.println("ppn found! vpn=" + item.TE.vpn);
				return item.TE.vpn;
			}
		}
		//System.out.println("ppn not found");
		return -1;
	}

	public invertedPageTableEntry getEntry(int ppn) {
		for(invertedPageTableEntry item: IPT) {
			if(item.TE.ppn == ppn) {
				return item;
			}
		}
		return null;
	}

	public static int getPPNfromClock() {
		while(VMkernel.getEntry(clocky).TE.used == true) {
			VMkernel.getEntry(clocky).TE.used  = false;
			clocky += 1;
			clocky = clocky%Machine.processor().getNumPhysPages();
		}
		int ppn = clocky;
		Lib.debug('d',"evicting: " + ppn);
		clocky += 1;
		clocky = clocky%Machine.processor().getNumPhysPages();
		Lib.debug('d',"new clock value: " + clocky);
		eviction(VMkernel.getEntry(ppn));
		return ppn;
	}

	private static int clocky = 0;

	public static void eviction(invertedPageTableEntry IPTE) {
		VMProcess currProcess = IPTE.process;
		int spn;
		int ppn = IPTE.TE.ppn;
		boolean spnExisted;
		byte[] memory = Machine.processor().getMemory();
		if(!currProcess.swapTable.containsKey(IPTE.TE.vpn)) {
			spn = getSPN();
			currProcess.swapTable.put(IPTE.TE.vpn, spn);
			spnExisted = false;
		}
		else {
			spn = currProcess.swapTable.get(IPTE.TE.vpn);
			spnExisted = true;
		}
		if(IPTE.TE.dirty || !spnExisted) {
			Lib.debug('d',"writing to swap file");
			swap.write(spn * pageSize, memory, ppn * pageSize, pageSize); // --------------------------------------------------------------
		}
		IPTE.TE.valid = false;
		IPT.remove(IPTE);
	}

	static int pageSize = 1024;

}

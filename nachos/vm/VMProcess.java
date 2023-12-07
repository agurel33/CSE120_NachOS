package nachos.vm;


import java.beans.PropertyEditor;
import java.io.EOFException;
import java.util.Arrays;
import java.util.HashMap;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {

	private int initialPC, initialSP;
	private int argc, argv;

	private static Lock userLocky = null;
	public HashMap<Integer, Integer> swapTable = null;
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		if(userLocky == null) {
			userLocky = new Lock();
		}
		if(swapTable == null) {
			swapTable = new HashMap<>();
		}
	}

	public boolean execute(String name, String[] args) {
		
		if (!this.load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "VMProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		Lib.debug(dbgProcess, "VMProcess.load: " + numPages + " pages in address space (" + Machine.processor().getNumPhysPages() + " physical pages)");

		/*
		 * Layout of the Nachos user process address space.
		 * The code above calculates the total number of pages
		 * in the address space for this executable.
		 *
		 * +------------------+
		 * | Code and data    |
		 * | pages from       |   size = num pages in COFF file
		 * | executable file  |
		 * | (COFF file)      |
		 * +------------------+
		 * | Stack pages      |   size = stackPages
		 * +------------------+
		 * | Arg page         |   size = 1
		 * +------------------+
		 *
		 * Page 0 is at the top, and the last page at the
		 * bottom is the arg page at numPages-1.
		 */


		if (!this.loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.debug(dbgProcess, "Writing VM!");
		userLocky.acquire();
		int total_amount = 0;
		int amount;
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		//TODO
		if (vaddr < 0 || vaddr >= memory.length) {
			userLocky.release();
			return 0;
		}

		if(length > pageSize) {
			int remainder = length % pageSize;
			int remainder2;
			if(remainder == 0) {
				remainder2 = 0;
			}
			else {
				remainder2 = 1;
			}
			int pagesNeeded = length / pageSize + remainder2;
			if(length > ((numPages - 9) * 1024)) {
				userLocky.release();
				return -1;
			}
			Lib.debug(dbgProcess, "pages needed: " + pagesNeeded);
			int offset_physical = Processor.offsetFromAddress(vaddr);

			for(int saber = 0; saber < pagesNeeded; saber++) {
				int virtualPageNum = Processor.pageFromAddress(vaddr + saber);
				if(pageTable[virtualPageNum].valid != true) {
					requestPage(vaddr + saber);
				}
				int physcialPageNum = pageTable[virtualPageNum].ppn;
				int physicalAddress = pageSize * physcialPageNum + offset_physical;

				if (physicalAddress < 0 || physicalAddress >= memory.length) {
					userLocky.release();
					return 0;
				}
				amount = Math.min(length, pageSize - offset_physical);
				offset_physical = 0;
				System.arraycopy(data, offset, memory, physicalAddress, amount);
				total_amount += amount;
				Lib.debug(dbgProcess, "curr amount at " + saber + "th page: " + amount + ", Total amount: " + total_amount);
				pageTable[virtualPageNum].dirty = true;
			}
		}
		else {
			int virtualPageNum = Processor.pageFromAddress(vaddr);
			if(pageTable[virtualPageNum].valid != true) {
				requestPage(vaddr);
			}
			int offset_physical = Processor.offsetFromAddress(vaddr);
			int physcialPageNum = pageTable[virtualPageNum].ppn;
			int physicalAddress = pageSize * physcialPageNum + offset_physical;

			if (physicalAddress < 0 || physicalAddress >= memory.length) {
				userLocky.release();
				return 0;
			}

			amount = Math.min(length, pageSize - offset_physical);
			System.arraycopy(data, offset, memory, physicalAddress, amount);
			total_amount = amount;
			pageTable[virtualPageNum].dirty = true;
		}
		
		userLocky.release();
		return total_amount;
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.debug(dbgProcess, "Reading VM!");
		userLocky.acquire();
		int total_amount = 0;
		int amount;
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length) {
			userLocky.release();
			return 0;
		}
		
		if(length > pageSize) {
			int offset_physical = Processor.offsetFromAddress(vaddr);
			int offy = offset_physical;
			//System.out.println("offset!: " + offset_physical);
			int remainder = length + offset_physical % pageSize;
			int remainder2;
			if(remainder == 0) {
				remainder2 = 0;
			}
			else {
				remainder2 = 1;
			}
			//System.out.println("Length: " + length + ", remainder2: " + remainder2);
			int pagesNeeded = length / pageSize + remainder2;
			if(length > ((numPages - 9) * 1024)) {
				userLocky.release();
				return -1;
			}
			Lib.debug(dbgProcess, "pages needed: " + pagesNeeded);
			
			for(int saber = 0; saber < pagesNeeded; saber++) {
				int virtualPageNum = Processor.pageFromAddress(vaddr + saber);
				if(pageTable[virtualPageNum].valid != true) {
					requestPage(vaddr + saber);
				}
				int physcialPageNum = pageTable[virtualPageNum].ppn;
				int physicalAddress = pageSize * physcialPageNum + offset_physical;

				if (physicalAddress < 0 || physicalAddress >= memory.length) {
					userLocky.release();
					return 0;
				}

				amount = Math.min(length, pageSize - offset_physical);
				if(saber == pagesNeeded - 1) {
					amount = length - total_amount;
				}
				offset_physical = 0;
				System.arraycopy(memory, physicalAddress, data, offset, amount);
				total_amount += amount;
				Lib.debug(dbgProcess, "curr amount at " + saber + "th page: " + amount + ", Total amount: " + total_amount);
			}
		}
		else {
			int virtualPageNum = Processor.pageFromAddress(vaddr);
			if(pageTable[virtualPageNum].valid != true) {
				requestPage(vaddr);
			}
			int offset_physical = Processor.offsetFromAddress(vaddr);
			int physcialPageNum = pageTable[virtualPageNum].ppn;
			int physicalAddress = pageSize * physcialPageNum + offset_physical;

			if (physicalAddress < 0 || physicalAddress >= memory.length) {
				userLocky.release();
				return 0;
			}

			amount = Math.min(length, pageSize - offset_physical);
			System.arraycopy(memory, physicalAddress, data, offset, amount);
			total_amount = amount;
		}

		userLocky.release();
		return total_amount;
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	public boolean loadSections() {
		// if (numPages > Machine.processor().getNumPhysPages() || numPages > UserKernel.linky.size()) {
		// 	coff.close();
		// 	Lib.debug(dbgProcess, "\tinsufficient physical memory");
		// 	return false;
		// }
		pageTable = new TranslationEntry[numPages];

		for(int i = 0; i < numPages; i++) {
			Lib.debug('d', "Number of total pages: " + numPages);
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
		for(int i=0; i < pageTable.length; i++) {
			if(swapTable.get(pageTable[i].vpn) != null) {
				int spn = swapTable.get(pageTable[i].vpn);
				VMKernel.releaseSPN(spn);
				swapTable.remove(pageTable[i].vpn);
			}
		}
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		//Lib.debug(dbgProcess, "Handling an exception...");
		switch (cause) {
		case Processor.exceptionPageFault:
			//start
			requestPage(Machine.processor().readRegister(Processor.regBadVAddr));
			return;
		default:
			super.handleException(cause);
			break;
		}
	}

	private void requestPage(int addy) {
		Lib.debug('d',"requesting files");

		userLocky.acquire();
		byte[] memory = Machine.processor().getMemory(); // --------------------------------------------------------------
		int page_to_load = addy / 1024;
		//Lib.debug(dbgProcess, "Entering requestPage for page: " + page_to_load);
		boolean previous_fault = false;

		if(swapTable.containsKey(page_to_load)) {
			previous_fault = true;
		}

		if (!VMKernel.linky.isEmpty()) {
			pageTable[page_to_load].ppn = VMKernel.getNextOpenPage();
			Lib.debug('d', "Physical pages left: " + VMKernel.linky.size());
			//Lib.debug(dbgProcess, "New page, got ppn: " + ppn);
		}
		else {
			pageTable[page_to_load].ppn = VMKernel.getPPNfromClock();
		} 
		int curr_ppn = pageTable[page_to_load].ppn;

		//clean UNTIL HERE!!!
		
		if(previous_fault) {
			//Lib.debug(dbgProcess, "Loading page from swap");
			//Lib.debug(dbgProcess, "file offset: " + (old_spn * pageSize));
			//Lib.debug(dbgProcess, "size of file: " + VMKernel.swap.length());
			//Lib.debug(dbgProcess, "storing in: " + (ppn * pageSize));
			int curr_spn = swapTable.get(page_to_load);
			VMKernel.swap.read(curr_spn * pageSize, memory,curr_ppn * pageSize, pageSize); // --------------------------------------------------------------
		}
		else {	
			boolean done = false;
			for(int i=0; i<coff.getNumSections(); i++) {
				CoffSection section = coff.getSection(i);
				
				for(int j=0; j < section.getLength(); j++) {
					int section_vpn = section.getFirstVPN() + j;
					if(page_to_load == section_vpn) {
						Lib.debug('d', "Loading page from coff");
						section.loadPage(j, pageTable[page_to_load].ppn);
						done = true;
						break;
					}
				}
				if(done) {
					break;
				}
			}

			if(!done) {
				//Lib.debug(dbgProcess, "Demand paging for stack");
				Lib.debug('d', "Loading page from stack/arg");
				//Lib.debug(dbgProcess, "filling from " + phy_addr);
				//Lib.debug(dbgProcess, "filling to" +( phy_addr + pageSize));
				Arrays.fill(memory, curr_ppn * pageSize, curr_ppn *pageSize + pageSize, (byte) 0); // --------------------------------------------------------------
			}
			
		}
		pageTable[page_to_load].valid = true;
		VMKernel.VMkernel.newEntry(this, pageTable[page_to_load]);
		userLocky.release();
		Lib.debug('d', "Exiting requestPage --------------");
		//System.out.println("End of LoadProcess");
		//System.out.println();
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}

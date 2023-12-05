package nachos.vm;


import java.io.EOFException;
import java.util.Arrays;

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
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		if(userLocky == null) {
			userLocky = new Lock();
		}
	}

	public boolean execute(String name, String[] args) {
		if (!load5(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	private boolean load5(String name, String[] args) {
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
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length) {
			userLocky.release();
			return 0;
		}

		if(length > pageSize) {
			int bytesRead = 0;
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
		}

		userLocky.release();
		return total_amount;
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.debug(dbgProcess, "Reading VM!");
		userLocky.acquire();
		int total_amount = 0;
		int amount;
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length) {
			userLocky.release();
			return 0;
		}
		
		if(length > pageSize) {
			
			int offset_physical = Processor.offsetFromAddress(vaddr);
			int offy = offset_physical;
			System.out.println("offset!: " + offset_physical);
			int remainder = length + offset_physical % pageSize;
			int remainder2;
			if(remainder == 0) {
				remainder2 = 0;
			}
			else {
				remainder2 = 1;
			}
			System.out.println("Length: " + length + ", remainder2: " + remainder2);
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
		int counter = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				pageTable[counter] = new TranslationEntry(vpn, -1, false, section.isReadOnly(), false, false);
				counter++;
			}
		}
		for(int abbi_sucks = 0; abbi_sucks < stackPages; abbi_sucks++) {
			pageTable[counter] = new TranslationEntry(counter, -1, false, false, false, false);
			counter++;
		}
		pageTable[counter] = new TranslationEntry(counter, -1,false,false,false,false);
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		switch (cause) {
		case Processor.exceptionPageFault:
			//start
			Processor proc = Machine.processor();
			int bad_address = proc.getBadAddress();
			System.out.println(bad_address + " bad");
			requestPage(bad_address);
			return;
		default:
			super.handleException(cause);
			break;
		}
	}

	private void requestPage(int addy) {
		byte[] memory = Machine.processor().getMemory();
		int page_to_load = Processor.pageFromAddress(addy);
		System.out.println(page_to_load + " page");
		int ppn = -1;
		boolean faulted = false;

		if(VMKernel.faultTable.get(page_to_load) != null) {
			faulted = true;
		}
		
		if (UserKernel.linky.size() > 0) {
			ppn = UserKernel.getNextOpenPage();
			pageTable[page_to_load].ppn = ppn;
			VMKernel.VMkernel.newEntry(this, pageTable[page_to_load]);
			if(VMKernel.faultTable.get(page_to_load) == null) {
				VMKernel.faultTable.put(page_to_load,true);
			}
		}
		if (ppn == -1) {
			while(VMKernel.VMkernel.IPT.get(clocky).TE.used == true) {
				VMKernel.VMkernel.IPT.get(clocky).TE.used  = false;
				clocky += 1;
				clocky = clocky%Machine.processor().getNumPhysPages();
			}
			int phys_page = clocky;
			clocky += 1;
			clocky = clocky%Machine.processor().getNumPhysPages();

			int bye_bye = VMKernel.VMkernel.vpnFromPpn(phys_page);


			VMKernel.VMkernel.IPT.get(bye_bye).TE.valid = false;
			int spn = VMKernel.getSPN();
			VMKernel.swapTable.put(bye_bye, spn);
			int old_addr = pageTable[bye_bye].ppn * pageSize;
			System.out.println("Bye_bye: " + bye_bye + ", and its ppn?: " + pageTable[bye_bye].ppn);
			System.out.println("Write spn!:" + spn  + " PPN: " + old_addr);
			VMKernel.swap.write(spn * pageSize, memory, old_addr, pageSize);
			//VMKernel.releasePage(pageTable[bye_bye].ppn);
			pageTable[bye_bye].ppn = -1;
			//ppn = VMKernel.getNextOpenPage();
			pageTable[page_to_load].ppn = phys_page;
		}
		//VMKernel.VMkernel.IPT.get(ppn).TE.valid = true;
		if(faulted) {
			//free ppn already, write from swap to physical 
			int old_spn = VMKernel.swapTable.get(page_to_load);
			//System.out.println("Read spn!:" + old_spn + " PPN: " + ppn);
			VMKernel.swap.read(old_spn * pageSize, memory,ppn * pageSize, pageSize);
			pageTable[page_to_load].valid = true;
			//VMKernel.releaseSPN(old_spn);
		}
		else {
			int coff_pages = 0;
			for(int i=0; i < coff.getNumSections(); i++) {
				CoffSection section = coff.getSection(i);
				coff_pages += section.getLength();
			}
			if(page_to_load >= 0 && page_to_load <= coff_pages) {
				//load coff page
				outerloop:
				for(int i = 0; i < coff.getNumSections(); i++) {
					CoffSection section = coff.getSection(i);
					for(int a=0; a < section.getLength(); a++) {
						int vpn = section.getFirstVPN() + a;
						if(vpn == page_to_load) {
							section.loadPage(a, pageTable[page_to_load].ppn);
							pageTable[page_to_load].valid = true;
							break outerloop;
						}
					}
				}
			}
			else {
				//load new page fill w/ zeros
				int page_desired = pageTable[page_to_load].ppn;
				pageTable[page_to_load].valid = true;
				int phy_addr = page_desired * pageSize;
				Arrays.fill(memory, phy_addr, phy_addr + pageSize, (byte) 0);
			}
		}
	}
	private static int clocky = 0;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}

package nachos.vm;


import java.util.Arrays;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {

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
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages() || numPages > UserKernel.linky.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
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
		int ppn = -1;
		boolean faulted = false;

		if(VMKernel.faultTable.get(page_to_load) == true) {
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
			int bye_bye = clocky;
			clocky += 1;
			clocky = clocky%Machine.processor().getNumPhysPages();
			VMKernel.VMkernel.IPT.get(bye_bye).TE.valid = false;
			int spn = VMKernel.getSPN();
			VMKernel.swapTable.put(bye_bye, spn);
			int old_addr = pageTable[bye_bye].ppn * pageSize;
			System.arraycopy(memory , old_addr, VMKernel.swap, spn * pageSize, pageSize);
			VMKernel.releasePage(pageTable[bye_bye].ppn);
			pageTable[bye_bye].ppn = -1;
			pageTable[page_to_load].ppn = VMKernel.getNextOpenPage();
		}
		if(faulted) {
			//free ppn already, write from swap to physical 
			int old_spn = VMKernel.swapTable.get(page_to_load);
			System.arraycopy(VMKernel.swap, old_spn, memory, ppn, pageSize);
			VMKernel.releaseSPN(old_spn);
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

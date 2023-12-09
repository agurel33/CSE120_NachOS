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
		System.out.println("entering write vm");
		if(length == 0) {
			return 0;
		}
		Lib.debug('c', "Writing VM!");
		userLocky.acquire();
		int amount;
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		//TODO
		int addressForVPN = numPages * pageSize;
		if (vaddr < 0 || vaddr > addressForVPN) {
			userLocky.release();
			return 0;
		}
		int virtual_offset = vaddr % pageSize; // why everytime
		int virtualPageNum = vaddr / pageSize;
		if(pageTable[virtualPageNum].valid != true) {
			requestPage(vaddr);
		}
		int physcialPageNum = pageTable[virtualPageNum].ppn;
		int physicalAddress = pageSize * physcialPageNum + virtual_offset;
		if (physicalAddress < 0 || physicalAddress >= memory.length) {
			Lib.debug('c', "ATTEMPTED TO ALLOCATE IMPOSSIBLE PAGE!");
			userLocky.release();
			return 0;
		}
		amount = Math.min(length, pageSize - virtual_offset);
		System.out.println("Writing " + amount + " bytes to " + vaddr + " address");
		System.arraycopy(data, offset, memory, physicalAddress, amount);

		int new_vaddr = vaddr + amount;
		Lib.debug('c', "offset: " + offset);
		int new_offset = offset + amount;
		int new_length = length - amount;
		userLocky.release();
		
		Lib.debug('c', new_vaddr + ", " + new_offset + ", " + new_length);
		return writeVirtualMemory(new_vaddr, data, new_offset,new_length) + amount;
		//Lib.debug('c', "printing curr total amount(in write): " + total_amount);




			// int new_length = length;

			// int first_offset = Processor.offsetFromAddress(vaddr);
			// int pagesNeeded = (int) Math.ceil((double)(length + first_offset) / (double)pageSize);
			// if(length > addressForVPN) {
			// 	userLocky.release();
			// 	//Lib.debug('c', "AHHHHHHHHH");
			// 	return -1;
			// }
			// Lib.debug(dbgProcess, "pages needed: " + pagesNeeded);
			

			// for(int saber = 0; saber < pagesNeeded; saber++) {
			// 	int virtualPageNum = Processor.pageFromAddress(vaddr + saber*pageSize);
			// 	int offset_physical = 0;
			// 	if(saber == 0) {
			// 		offset_physical = Processor.offsetFromAddress(vaddr + saber*pageSize);
			// 	}
			// 	if(saber == pagesNeeded - 1) {
			// 		offset_physical = first_offset; //wtf
			// 	}
			// 	Lib.debug('w',"curr virtual page: " + virtualPageNum);
			// 	if(pageTable[virtualPageNum].valid != true) {
			// 		requestPage(vaddr + saber*pageSize);
			// 	}
			// 	int physcialPageNum = pageTable[virtualPageNum].ppn;
			// 	int physicalAddress = pageSize * physcialPageNum + offset_physical;

			// 	if (physicalAddress < 0 || physicalAddress >= memory.length) {
			// 		userLocky.release();
			// 		return 0;
			// 	}
			// 	amount = Math.min(new_length, pageSize - offset_physical);//here?
			// 	//offset_physical = 0;//???
			// 	System.arraycopy(data, offset_physical, memory, physicalAddress, amount);//not right?
			// 	new_length -= amount;
			// 	total_amount += amount;
			// 	Lib.debug(dbgProcess, "curr amount at " + saber + "th page: " + amount + ", Total amount: " + total_amount);
			// 	pageTable[virtualPageNum].dirty = true;
			// }
		// else {
		// 	int virtualPageNum = Processor.pageFromAddress(vaddr);
		// 	if(pageTable[virtualPageNum].valid != true) {
		// 		requestPage(vaddr);
		// 	}
		// 	int offset_physical = Processor.offsetFromAddress(vaddr);
		// 	int physcialPageNum = pageTable[virtualPageNum].ppn;
		// 	int physicalAddress = pageSize * physcialPageNum + offset_physical;

		// 	if (physicalAddress < 0 || physicalAddress >= memory.length) {
		// 		userLocky.release();
		// 		//Lib.debug('c', "why are we here");
		// 		return 0;
		// 	}

		// 	amount = Math.min(length, pageSize - offset_physical);
		// 	System.arraycopy(data, offset, memory, physicalAddress, amount);
		// 	total_amount += amount;
		// 	Lib.debug('c', "printing curr total amount(outside recursive in write): " + total_amount);
		// 	pageTable[virtualPageNum].dirty = true;
		// }
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		System.out.println("entering read vm");
		if(length == 0) {
			return 0;
		}

		Lib.debug('c', "Reading VM!");
		userLocky.acquire();
		int amount;
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		int addressForVPN = numPages * pageSize;
		if (vaddr < 0 || vaddr > addressForVPN) {
			userLocky.release();
			return 0;
		}

		int virtual_offset = vaddr % pageSize; // why everytime
		int virtualPageNum = vaddr / pageSize;
		if(pageTable[virtualPageNum].valid != true) {
			requestPage(vaddr);
		}
		int physcialPageNum = pageTable[virtualPageNum].ppn;
		int physicalAddress = pageSize * physcialPageNum + virtual_offset;
		if (physicalAddress < 0 || physicalAddress >= memory.length) {
			Lib.debug('c', "ATTEMPTED TO ALLOCATE IMPOSSIBLE PAGE!");
			userLocky.release();
			return 0;
		}

		amount = Math.min(length, pageSize - virtual_offset);
		System.out.println("Writing " + amount + " bytes to " + vaddr + " address");
		System.arraycopy(memory, physicalAddress, data, offset, amount);

		int new_vaddr = vaddr + amount;
		int new_offset = offset + amount;
		int new_length = length - amount;
		userLocky.release();

		Lib.debug('c', new_vaddr + ", " + new_offset + ", " + new_length);
		return readVirtualMemory(new_vaddr, data, new_offset, new_length) + amount;
			// int new_length = length;
			
			// //System.out.println("offset!: " + offset_physical);
			// int first_offset = Processor.offsetFromAddress(vaddr);
			// int pagesNeeded = (int) Math.ceil((double)(length + first_offset) / (double)pageSize);
			// if(length > addressForVPN) {
			// 	userLocky.release();
			// 	//Lib.debug('c', "AHHHHHHHHH");
			// 	return -1
			// 	;
			// }
			// Lib.debug(dbgProcess, "pages needed: " + pagesNeeded);
			
			// for(int saber = 0; saber < pagesNeeded; saber++) {
			// 	int virtualPageNum = Processor.pageFromAddress(vaddr + saber*pageSize);
			// 	int offset_physical = 0;
			// 	if(saber == 0) {
			// 		offset_physical = Processor.offsetFromAddress(vaddr + saber*pageSize);
			// 	}
			// 	if(saber == pagesNeeded - 1) {
			// 		offset_physical = first_offset;
			// 	}
			// 	if(pageTable[virtualPageNum].valid != true) {
			// 		requestPage(vaddr + saber*pageSize);
			// 	}
			// 	int physcialPageNum = pageTable[virtualPageNum].ppn;
			// 	int physicalAddress = pageSize * physcialPageNum + offset_physical;

			// 	if (physicalAddress < 0 || physicalAddress >= memory.length) {
			// 		userLocky.release();
			// 		return 0;
			// 	}
			// 	amount = Math.min(new_length, pageSize - offset_physical);
			// 	//offset_physical = 0;
			// 	System.arraycopy(memory, physicalAddress, data, offset_physical, amount);
			// 	new_length -= amount;
			// 	total_amount += amount;
			// 	Lib.debug('c', "curr amount at " + saber + "th page: " + amount + ", Total amount: " + total_amount);
			// }
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

		Lib.debug('d', "Number of total pages: " + numPages);

		for(int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, i, false, false, false, false);
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

		//userLocky.acquire();
		byte[] memory = Machine.processor().getMemory(); // --------------------------------------------------------------
		int page_to_load = addy / 1024;
		//Lib.debug(dbgProcess, "Entering requestPage for page: " + page_to_load);

		if (!VMKernel.linky.isEmpty()) {
			pageTable[page_to_load].ppn = VMKernel.getNextOpenPage();
			Lib.debug('d', "Physical pages left: " + VMKernel.linky.size());
			//Lib.debug(dbgProcess, "New page, got ppn: " + ppn);
		}
		else {
			pageTable[page_to_load].ppn = VMKernel.getPPNfromClock();
			Lib.debug('d', "Got ppn from evicting: " + pageTable[page_to_load].ppn);
		} 
		int curr_ppn = pageTable[page_to_load].ppn;

		//clean UNTIL HERE!!!
		Lib.debug('d', "Current ppn: " + curr_ppn + ", trying to load page: " + page_to_load);
		if(swapTable.containsKey(page_to_load)) {
			//Lib.debug(dbgProcess, "Loading page from swap");
			//Lib.debug(dbgProcess, "file offset: " + (old_spn * pageSize));
			//Lib.debug(dbgProcess, "size of file: " + VMKernel.swap.length());
			//Lib.debug(dbgProcess, "storing in: " + (ppn * pageSize));
			int curr_spn = swapTable.get(page_to_load);
			int swap_read = VMKernel.swap.read(curr_spn * pageSize, memory,curr_ppn * pageSize, pageSize);
			Lib.debug('d', "value from swap read: " + swap_read);
		}
		else {	
			boolean done = false;
			for(int i=0; i<coff.getNumSections(); i++) {
				if(done) {
					break;
				}
				CoffSection section = coff.getSection(i);
				for(int j=0; j < section.getLength(); j++) {
					int section_vpn = section.getFirstVPN() + j;
					if(page_to_load == section_vpn) {
						Lib.debug('d', "Loading page from coff: " + j);
						section.loadPage(j, pageTable[page_to_load].ppn);
						done = true;
						break;
					}
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
		//userLocky.release();
		Lib.debug('d', "Exiting requestPage --------------");
		//System.out.println("End of LoadProcess");
		//System.out.println();
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}

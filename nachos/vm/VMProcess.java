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
	private static Lock pageLock = null;
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
		if(pageLock == null) {
			pageLock = new Lock();
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
		if(length == 0) {
			return 0;
		}
		userLocky.acquire();
		int amount;
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();

		int addressForVPN = numPages * pageSize;
		if (vaddr < 0 || vaddr > addressForVPN) {
			userLocky.release();
			return 0;
		}
		int virtual_offset = vaddr % pageSize; // why everytime
		int virtualPageNum = vaddr / pageSize;
		if(virtualPageNum >= numPages) {
			userLocky.release();
			return -1;
		}
		if(pageTable[virtualPageNum].valid != true) {
			requestPage(vaddr);
		}
		int physcialPageNum = pageTable[virtualPageNum].ppn;
		int physicalAddress = pageSize * physcialPageNum + virtual_offset;
		if (physicalAddress < 0 || physicalAddress >= memory.length) {
			userLocky.release();
			return 0;
		}
		amount = Math.min(length, pageSize - virtual_offset);
		System.arraycopy(data, offset, memory, physicalAddress, amount);

		int new_vaddr = vaddr + amount;
		Lib.debug('c', "offset: " + offset);
		int new_offset = offset + amount;
		int new_length = length - amount;
		userLocky.release();

		Lib.debug('c', new_vaddr + ", " + new_offset + ", " + new_length);
		return writeVirtualMemory(new_vaddr, data, new_offset,new_length) + amount;
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		if(length == 0) {
			return 0;
		}

		Lib.debug('c', "Reading VM!");
		userLocky.acquire();
		int amount;
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();

		int addressForVPN = numPages * pageSize;
		if (vaddr < 0 || vaddr > addressForVPN) {
			userLocky.release();
			return 0;
		}

		int virtual_offset = vaddr % pageSize; 
		int virtualPageNum = vaddr / pageSize;
		if(virtualPageNum >= numPages) {
			userLocky.release();
			return -1;
		}
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
		System.arraycopy(memory, physicalAddress, data, offset, amount);

		int new_vaddr = vaddr + amount;
		int new_offset = offset + amount;
		int new_length = length - amount;
		userLocky.release();

		Lib.debug('c', new_vaddr + ", " + new_offset + ", " + new_length);
		return readVirtualMemory(new_vaddr, data, new_offset, new_length) + amount;
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	public boolean loadSections() {
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
		switch (cause) {
		case Processor.exceptionPageFault:
			requestPage(Machine.processor().readRegister(Processor.regBadVAddr));
			return;
		default:
			super.handleException(cause);
			break;
		}
	}

	private void requestPage(int addy) {
		pageLock.acquire();

		byte[] memory = Machine.processor().getMemory(); // --------------------------------------------------------------
		int page_to_load = addy / 1024;

		if (!VMKernel.linky.isEmpty()) {
			pageLock.release();
			pageTable[page_to_load].ppn = VMKernel.getNextOpenPage();
			pageLock.acquire();
		}
		else {
			pageLock.release();
			pageTable[page_to_load].ppn = VMKernel.getPPNfromClock();
			pageLock.acquire();
		} 
		int curr_ppn = pageTable[page_to_load].ppn;

		//clean UNTIL HERE!!!
		if(swapTable.containsKey(page_to_load)) {
			int curr_spn = swapTable.get(page_to_load);
			pageLock.release();
			VMKernel.swap.read(curr_spn * pageSize, memory,curr_ppn * pageSize, pageSize);
			pageLock.acquire();
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
						pageLock.release();
						section.loadPage(j, pageTable[page_to_load].ppn);
						pageLock.acquire();
						done = true;
						break;
					}
				}
			}
			if(!done) {
				pageLock.release();
				Arrays.fill(memory, curr_ppn * pageSize, curr_ppn *pageSize + pageSize, (byte) 0);
				pageLock.acquire();
			}
			
		}
		pageTable[page_to_load].valid = true;
		pageLock.release();
		VMKernel.VMkernel.newEntry(this, pageTable[page_to_load]);
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}

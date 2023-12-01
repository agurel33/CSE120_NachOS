package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
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
				pageTable[counter] = new TranslationEntry(vpn, UserKernel.getNextOpenPage(), false, section.isReadOnly(), false, false);
				counter++;
			}
		}

		for(int abbi_sucks = 0; abbi_sucks < stackPages; abbi_sucks++) {
			pageTable[counter] = new TranslationEntry(counter, UserKernel.getNextOpenPage(), false, false, false, false);
			counter++;
		}
		pageTable[counter] = new TranslationEntry(counter,UserKernel.getNextOpenPage(),false,false,false,false);
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
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionPageFault:
			//start
			requestPage();
		default:
			super.handleException(cause);
			break;
		}
	}

	private void requestPage() {
		Processor proc = Machine.processor();
		int bad_address = proc.getBadAddress();
		int page_to_load;
		if(bad_address % pageSize != 0){ 
			page_to_load = bad_address / pageSize + 1;
		}
		else {
			page_to_load = bad_address / pageSize;
		}

		int coff_pages = 0;
		for(int i=0; i < coff.getNumSections(); i++) {
			CoffSection section = coff.getSection(i);
			coff_pages += section.getLength();
		}
		if(page_to_load >= 0 && page_to_load <= coff_pages) {
			//load coff page
			for(int i = 0; i < coff.getNumSections(); i++) {
				CoffSection section = coff.getSection(i);
				for(int a=0; a < section.getLength(); a++) {
					int vpn = section.getFirstVPN() + a;
					if(vpn == page_to_load) {
						section.loadPage(a, pageTable[page_to_load].ppn);
					}
				}
			}
		}
		else {
			//load new page fill w/ zeros
			int page_desired = pageTable[page_to_load].ppn;
			
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}

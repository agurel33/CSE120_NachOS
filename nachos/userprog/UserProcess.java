package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */

	private static Lock userLock = null;
	private static Lock IDLock = null;
	public int parentID;
	public HashMap<Integer,Integer> status_of_children;

	public UserProcess() {
		//int numPhysPages = Machine.processor().getNumPhysPages();
		if(IDLock == null) {
			IDLock = new Lock();
		}

		fileTable = new OpenFile[16];
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();
		fs = (StubFileSystem) ThreadedKernel.fileSystem;

		status_of_children = new HashMap<>();

		IDLock.acquire();
		processID = nextProcess;
		nextProcess++;
		IDLock.release();
		parentID = processID;

		UserKernel.inputHashMap(processID, this);

		if(myChildren == null) {
			myChildren = new ArrayList<>();
		}

		if(userLock == null) {
			userLock = new Lock();
		}
		if(IDLock == null) {
			IDLock = new Lock();
		}

		UserKernel.numProc += 1;
		//Needs to be in a lock
		// for (int i = 0; i < numPhysPages; i++)
		// 	pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
	}

	public UserProcess(int parentID) {
		if(IDLock == null) {
			IDLock = new Lock();
		}

		UserKernel.numProc += 1;

		fileTable = new OpenFile[16];
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();
		fs = (StubFileSystem) ThreadedKernel.fileSystem;


		status_of_children = new HashMap<>();

		IDLock.acquire();
		processID = nextProcess;
		nextProcess++;
		IDLock.release();

		if(parentID == -1) {
			this.parentID = processID;
		}
		else {
			this.parentID = parentID;
		}

		if(myChildren == null) {
			myChildren = new ArrayList<>();
		}

		if(userLock == null) {
			userLock = new Lock();
		}
		if(IDLock == null) {
			IDLock = new Lock();
		}
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.debug(dbgProcess, "Reading VM!");
		userLock.acquire();
		int total_amount = 0;
		int amount;
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length) {
			userLock.release();
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
				userLock.release();
				return -1;
			}
			Lib.debug(dbgProcess, "pages needed: " + pagesNeeded);
			int offset_physical = Processor.offsetFromAddress(vaddr);
			
			for(int saber = 0; saber < pagesNeeded; saber++) {
				int virtualPageNum = Processor.pageFromAddress(vaddr + saber);
				
				int physcialPageNum = pageTable[virtualPageNum].ppn;
				int physicalAddress = pageSize * physcialPageNum + offset_physical;

				if (physicalAddress < 0 || physicalAddress >= memory.length) {
					userLock.release();
					return 0;
				}

				amount = Math.min(length, pageSize - offset_physical);
				offset_physical = 0;
				System.arraycopy(memory, physicalAddress, data, offset, amount);
				total_amount += amount;
				Lib.debug(dbgProcess, "curr amount at " + saber + "th page: " + amount + ", Total amount: " + total_amount);
			}
		}
		else {
			int virtualPageNum = Processor.pageFromAddress(vaddr);
			int offset_physical = Processor.offsetFromAddress(vaddr);
			int physcialPageNum = pageTable[virtualPageNum].ppn;
			int physicalAddress = pageSize * physcialPageNum + offset_physical;

			if (physicalAddress < 0 || physicalAddress >= memory.length) {
				userLock.release();
				return 0;
			}

			amount = Math.min(length, pageSize - offset_physical);
			System.arraycopy(memory, physicalAddress, data, offset, amount);
			total_amount = amount;
		}

		userLock.release();
		return total_amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.debug(dbgProcess, "Writing VM!");
		userLock.acquire();
		int total_amount = 0;
		int amount;
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length) {
			userLock.release();
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
				userLock.release();
				return -1;
			}
			Lib.debug(dbgProcess, "pages needed: " + pagesNeeded);
			int offset_physical = Processor.offsetFromAddress(vaddr);

			for(int saber = 0; saber < pagesNeeded; saber++) {
				int virtualPageNum = Processor.pageFromAddress(vaddr + saber);
				
				int physcialPageNum = pageTable[virtualPageNum].ppn;
				int physicalAddress = pageSize * physcialPageNum + offset_physical;

				if (physicalAddress < 0 || physicalAddress >= memory.length) {
					userLock.release();
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
			int offset_physical = Processor.offsetFromAddress(vaddr);
			int physcialPageNum = pageTable[virtualPageNum].ppn;
			int physicalAddress = pageSize * physcialPageNum + offset_physical;

			if (physicalAddress < 0 || physicalAddress >= memory.length) {
				userLock.release();
				return 0;
			}

			amount = Math.min(length, pageSize - offset_physical);
			System.arraycopy(data, offset, memory, physicalAddress, amount);
			total_amount = amount;
		}

		userLock.release();
		return total_amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

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

		Lib.debug(dbgProcess, "UserProcess.load: " + numPages + " pages in address space (" + Machine.processor().getNumPhysPages() + " physical pages)");

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

		if (!loadSections())
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
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		pageTable = new TranslationEntry[numPages];
		// load sections
		//Lib.debug(dbgProcess, "Number of sections: " + coff.getNumSections());
		int counter = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				//Lib.debug(dbgProcess, "Entering page #: " + counter);
				pageTable[counter] = new TranslationEntry(vpn, UserKernel.getNextOpenPage(), true, section.isReadOnly(), false, false);
				//Lib.debug(dbgProcess, "Curr VPN: " + vpn + ", Curr PNP: " + pageTable[vpn].ppn);
				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, pageTable[counter].ppn);
				counter++;
			}
			//Lib.debug(dbgProcess, "finished loading COFF, counter is: " + counter);
		}

		for(int abbi_sucks = 0; abbi_sucks < stackPages; abbi_sucks++) {
			pageTable[counter] = new TranslationEntry(counter, UserKernel.getNextOpenPage(), true, false, false, false);
			counter++;
			//Lib.debug(dbgProcess, "loaded " + abbi_sucks + "th stack");
		}
		pageTable[counter] = new TranslationEntry(counter,UserKernel.getNextOpenPage(),true,false,false,false);

		//Lib.debug(dbgProcess, "loaded all sections");
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < numPages; i++) {
			int ppn = pageTable[i].ppn;
			UserKernel.releasePage(ppn);
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		Lib.debug(dbgProcess, "UserProcess.handleHalt");

		if(processID != 0) {
			return -1;
		}

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	    // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");

		System.out.println("We entereed handleExit");

		for(int x = 0; x < fileTable.length; x++) {
			OpenFile curr = fileTable[x];
			if(curr != null) {
				curr.close();
			}
		}
		
		status_of_children.put(processID, status);

		for(int y = 0; y < pageTable.length; y++) {
			TranslationEntry curry = pageTable[y];
			int ppn = curry.ppn;
			UserKernel.linky.add(ppn);
		}
		unloadSections();

		for(int z = 0; z < myChildren.size(); z++) {
			int childy = myChildren.get(z);
			UserProcess childProcessy = UserKernel.getHashMap(childy);
			if(childProcessy != null) {
				childProcessy.parentID = -1;
			}
		}


		if(parentID != -1) {
			UserProcess parent = UserKernel.getHashMap(parentID);
			if(parent != null) {
				boolean banana = Machine.interrupt().disable();
				parent.thread.ready();
				Machine.interrupt().restore(banana);
			}
		}
		System.out.println("do we get here ");


		UserKernel.numProc -= 1;
		System.out.println("curr num processes: " + UserKernel.numProc);

		System.out.println("We are leaving handleExit");
		
		if(UserKernel.numProc == 0) {
			Kernel.kernel.terminate();
		}

		KThread.finish();

		return 0;
	}

	private int handleWrite(int fd, int pt, int size) {
		if(fd < 0 || fd > 15) {
			return -1;
		}
		if(size == 0) {
			return 0;
		}
		if(size < 0) {
			return -1;
		}
		byte[] memory = Machine.processor().getMemory();

		if(pt < 0 || pt > memory.length) {
			return -1;
		}
		byte[] temp = new byte[size];
		int success = readVirtualMemory(pt, temp, 0, size);
		if(success != size) {
			System.out.println(success);
			return -1;
		}
		if(fileTable[fd] == null) {
			System.out.println(fd);
			return -1;
		}
		int greatSuccess = fileTable[fd].write(temp,0,size);
		if(greatSuccess != size) {
			System.out.println(greatSuccess);
			return -1;
		}
		return greatSuccess;
	}

	private int handleRead(int fd, int pt, int size) {
		if(fd < 0 || fd > 15) {
			return -1;
		}
		if(size == 0) {
			return 0;
		}
		if(size < 1) {
			return -1;
		}
		byte[] memory = Machine.processor().getMemory();

		if(pt < 0 || pt > memory.length) {
			return -1;
		}
		byte[] temp = new byte[size];
		if(fileTable[fd] == null) {
			return -1;
		}
		int success = fileTable[fd].read(temp,0,size);
		if(success != size) {
			return -1;
		}
		int greatSuccess = writeVirtualMemory(pt, temp);
		if(greatSuccess != size) {
			return -1;
		}
		return greatSuccess;
	}

	private int handleCreate(int name_pointer) {
		String name = readVirtualMemoryString(name_pointer, 256);
		if(name == null) {
			return -1;
		}
		int index = -1;
		for(int i = 2; i < 16; i++) {
			if(fileTable[i] == null && index == -1) {
				index = i;
			}
			else if(fileTable[i] != null && fileTable[i].getName().equals(name)) {
				fs.remove(name);
				OpenFile newfile = fs.open(name,true);
				fileTable[i] = newfile;
				return i;
			}
		}
		if(index != -1) {
			//System.out.println("checkpoint (" + index + ")");
			OpenFile newfile = fs.open(name, true);
			fileTable[index] = newfile;
			//System.out.println("Filename1: " + fileTable[index].getName());
			//System.out.println("Filename2: " + newfile.getName());
			return index;
		}
		return -1;
	}

	private int handleOpen(int name_pointer) {
		String name = readVirtualMemoryString(name_pointer, 256);
		if(name == null) {
			return -1;
		}
		int index = -1;
		for(int i = 0; i < 16; i++) {
			if(fileTable[i] != null && fileTable[i].getName().equals(name)) {
				return i;
			}
			else if(fileTable[i] == null && index == -1) {
				index = i; 
			}
		}
		if(index != -1) {
			OpenFile newfile = fs.open(name, false);
			fileTable[index] = newfile;
			return index;
		}
		return -1;
	}

	private int handleClose(int fd) {
		if(fd < 0 || fd > 15) {
			return -1;
		}
		if(fileTable[fd] == null) {
			return -1;
		}
		OpenFile oldFile = fileTable[fd];
		oldFile.close();
		fileTable[fd] = null;
		return 0;
	}

	private int handleUnlink(int name_pointer) {
		String name = readVirtualMemoryString(name_pointer, 256);
		if(name == null) {
			return -1;
		}
		fs.remove(name);
		for(int i=0; i < 16; i++) {
			if(fileTable[i] != null && fileTable[i].getName().equals(name)) {
				fileTable[i] = null;
			}
		}
		return 0;
	}

	private int handleExec(int file_pointer, int num_args, int array_pointer) {
		System.out.println("We entereed handleExec");
		byte[] memory = Machine.processor().getMemory();
		if(file_pointer <= 0 || file_pointer > memory.length) {
			return -1;
		}
		if(num_args < 0) {
			return -1;
		}
		else if(num_args != 0) {
			if(array_pointer <= 0 || array_pointer > memory.length) {
				return -1;
			}
		}
		

		String file_name = readVirtualMemoryString(file_pointer, 256);

		UserProcess processy = new UserProcess(processID);
		String[] args_array = new String[num_args];

		for(int capwn = 0; capwn < num_args; capwn++) {
			byte[] curr_arg = new byte[4];
			int success = readVirtualMemory(array_pointer + 4 * capwn, curr_arg, 0, 4);
			if(curr_arg == null || success != 4) {
				return -1;
			}
			int curr_pointer = Lib.bytesToInt(curr_arg, 0);
			String argy = readVirtualMemoryString(curr_pointer, 256);
			args_array[capwn] = argy;
		}

		int nextChild;
		IDLock.acquire();
		nextChild = nextProcess;
		myChildren.add(nextChild);
		System.out.println(file_name);
		boolean success = processy.execute(file_name, args_array);
		IDLock.release();
		if(!success) {
			return -1;
		}

		System.out.println("We are leaving handleExec");
		return nextChild;
	}

	private int handleJoin(int childId, int status_pointer) {
		System.out.println("We entereed handleJoin");
		if(!myChildren.contains(childId)) {
			return -1;
		}

		UserKernel.getHashMap(childId);

		byte[] memory = Machine.processor().getMemory();
		if(status_pointer < 0 || status_pointer > memory.length) {
			return -1;
		}

		

		UserProcess childprocess = UserKernel.getHashMap(childId);
		if(childprocess == null) {
			return -1;
		}
		childprocess.thread.join();

		myChildren.remove(childId);

		// byte[] memory = Machine.processor().getMemory();
		// if(status_pointer < 0 || status_pointer > memory.length) {
		// 	return - 1;
		// }
		// String status = readVirtualMemoryString(status_pointer, 256);
		Integer status = status_of_children.get(childId);

		if(status != null) {
			byte[] statty = Lib.bytesFromInt(status);
			writeVirtualMemory(status_pointer, statty);
		}
		System.out.println("We are leaving handleJoin");


		return status;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallWrite:
			return handleWrite(a0,a1,a2);
		case syscallRead:
			return handleRead(a0,a1,a2);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0,a1);

		

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** This process's file table. */
	protected OpenFile[] fileTable;

	protected StubFileSystem fs;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
        protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static int nextProcess = 0;
	private int processID;
	private static ArrayList<Integer> myChildren = null; 
}

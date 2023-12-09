Members: Abigail Shilts, Arda Gurel

We wrote code to allow for virtual paging. We created a method that dynamically allocates pysical memory to virtual pages by process.
When more memory is needed and all pysical pages are being used it will save the least recently used content to the swap file and
return that memory chunk. Additionally, when attempting to access contents stored in the swap file it is able to retrieve it. We also
implemented a dirty bit allowing for us to know if a part of the swapfile needs to be overwritten or if it has remained unchanged.

The code had base level functionality. We spent large amounts of time on odd bugs and thus had little time left for testing and
as a result only were able to ensure it passes swap4, swap5, and write10.

Group members worked together, pair programing for the duration of the project.
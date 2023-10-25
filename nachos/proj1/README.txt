Arda Gurel, Abigail Shilts
We wrote code in multiple places, to begin with in the Alarm class we created timer interupt and wait until methods to 
create functionality allowing for sleeping threads for a controlled amount of time. We also wrote the join method in the KThreads class
that causes a thread to wait for the completion of another before continuing allowing for better snynchronization and again, more user
control of thread execution order. Additionally we implemented the Condition2 class which has the same functionality as Condition, 
however, this class instead of using semaphores uses locks and intentionality with timer interupts to create atomicity and mimic the
semaphores in the Condition class. This also included a sleepFor method in the Condition2 class that utilized the functions of alarm
to again, give control of how long a thread was put to sleep. Lastly we also implemented the Rendezvous class which allows for threads
to, in a safe way, trade variables between them managing concurency.

At the moment we have used the basic tests provided and a minimal number of our own tests, however everything appears to be functioning 
properly, we are using this submission to ensure it compiles properly on gradescope and we pass public tests. Will make future submissions
with heavier testing for hidden tests.

Group members worked together on this project, almost exclusively pair programing and collaborating to find solutions

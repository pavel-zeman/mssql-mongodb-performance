# Performance of SQL Server drivers - Node.js vs. Java
When considering platform to use when working with large data volumes stored by SQL server, performance of the SQL Server drivers is one of the aspects to consider. This repository presents a simple benchmark, which compares the following platforms and their SQL Server drivers:
* Node.js mssql module with Tedious driver - https://www.npmjs.com/package/mssql
* Java JDBC library - https://docs.microsoft.com/en-us/sql/connect/jdbc/microsoft-jdbc-driver-for-sql-server

If you are not interested in any details, you can jump directly to the summary at the end.

# Environment
Following virtual servers are used for the benchmark. They are connected to a 1 Gbps local network with ping time less than 0.5 ms. Before running the benchmark, CPU usage is close to zero and there is more than 3 GB of free RAM.
## DB server
  * HW
    * 2 vCPU Intel Xeon Silver 4210 @ 2.2 GHz
    * 10 GB RAM
    * 100 GB SSD
  * SW
    * Windows Server 2019 Standard
    * SQL Server 2017 Developer Edition
## Application server
  * HW
    * 2 vCPU Intel Xeon Silver 4210 @ 2.2 GHz
    * 4 GB RAM
    * 60 GB SSD
  * SW
    * CentOS 8.1
    * OpenJDK 13.0.2.8 run with -Xmx128m
    * Node.js 13.10.1
  
# Benchmark
The benchmark is based on a simple table created using the following statement:
```sql
create table tsdata(
  id bigint not null, 
  created datetime2(0) not null, 
  value float not null, 
  primary key(id)
);
```

Using this table, following statements are executed:
* Truncate of the table to make sure that it is empty.
* Insert of 100K records. The records are inserted using SQL Server-specific bulk operation. In Java, standard JDBC batching could be used as well, but based on the measurements the bulk operation is many (about 6 to 7) times faster.
* Update of the 100K records. There are following update methods used:
  * Java supports batching. This can be used to prepare an update statement and execute it as a batch with 100K input records.
  * There is no batching supported in Node.js. In this case (and in Java as well so that we get comparable results), the update is done as follows:
    * A temporary table is created.
    * New data is inserted into the temporary table.
    * Original table is updated using a join with the temporary table.
```sql
      update d 
      set d.value = s.value 
      from tsdata d inner join #tsdatatemp s 
      on d.id = s.id    
```
* Select of the 100K records.

The whole benchmark is executed 10 times and runtime and CPU time of each operation is measured. The CPU time is measured for the process as a whole, which also includes CPU time of garbage collection running in parallel. As a result, the CPU time may be higher thatn runtime.

# Results
Both Node.js and Java need some time to initialize, JIT the code, connect to DB etc. As a result, when a statement is executed for the first time, its runtime is much higher. To handle this situation, the slowest runtime of each statement is discarded before processing the results. The remaining runtimes are used to calculate average with the results presented in the following table. The same applies to CPU times.

Statement|Runtime Node.js [ms]|Runtime Java [ms]|Ratio of Node.js vs. Java|CPU time Node.js [ms]|CPU time Java [ms]|Ratio of CPU time of Node.js vs. Java
---------|--------------------|-----------------|-------------------------|---------------------|------------------|------------------------------------
Insert|797|253|3.15|848|218|3.89
Update|1163|706|1.65|558|50|11.16
Batch update|N/A|1445|N/A|N/A|168|N/A
Select|507|108|4.69|512|166|3.08

Memory consumption can be an issue as well. In both Node.js and Java the memory consumption varies during the test so it's not possible to provide a single number. On the other hand, we can at least get an idea about the memory consumption by measuring it in regular intervals when the benchmark is run using the following script (the script works for Java, similar one can be created for Node.js):
```bash
while sleep 1; do  ps -o pid,user,rss,args ax | grep java | grep -v grep; done
```

Using this script we can get [RSS](https://en.wikipedia.org/wiki/Resident_set_size) values as shown in the following table.

Platform|Minimum [MB]|Maximum[MB]
--------|------------|-----------
Node.js|120|240
Java|137|202

# Summary
* Java can be about 2 to 5 times faster when working with SQL Server than Node.js in the scenarios measured in the benchmark. However, in real scenarios involving more complex tables and SQL statements, the ratio can get closer to 1.
* Java can consume about 3 to 11 times less CPU time.
* Memory consumption of Java and Node.js is comparable.

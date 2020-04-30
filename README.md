# Performance of SQL Server and MongoDB drivers - Node.js vs. Java
When considering platform to use when working with large data volumes stored by SQL Server or MongoDB, performance of the respective drivers is one of the aspects to consider. This repository presents a simple benchmark, which compares the following platforms and their SQL Server and MongoDB drivers:
* Node.js mssql module with Tedious driver version 6.2.0 - https://www.npmjs.com/package/mssql
* Node.js mongodb module version 3.5.6 - https://www.npmjs.com/package/mongodb
* Java JDBC library version 8.2.2.jre13 - https://docs.microsoft.com/en-us/sql/connect/jdbc/microsoft-jdbc-driver-for-sql-server
* Java MongoDB driver version 3.12.3 - https://mongodb.github.io/mongo-java-driver/

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
## SQL Server
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

The whole benchmark is executed 21 times as follows:
* Runtime and CPU time is measured for each operation.
* Garbage collection is explicitly triggered after the operation and included in operation times. The rationale behind this is as follows:
  * Minimize influence of garbage allocated by an operation on the following operation(s).
  * Penalize operations, which generate a lot of garbage.
* The CPU time is measured for the process as a whole, which also includes CPU time of garbage collection running in parallel. As a result, the CPU time may be higher than runtime.

## MongoDB
The benchmark for MongoDB is similar to SQL Server with the following differences:
* A single collection with the documents of the following type is used for the test: `{ "_id" : 0, "created" : ISODate("1970-01-01T00:16:40Z"), "value" : 0 }`. The collection has only the default index on `_id`.
* In Java, the API based on POJOs is used whenever possible (see https://mongodb.github.io/mongo-java-driver/3.5/driver/getting-started/quick-start-pojo/).

# Results
Both Node.js and Java need some time to initialize, JIT the code, connect to DB etc. As a result, when a statement is executed for the first time, its runtime is much higher. To handle this situation, the results of the first 10 executions of each operation are discarded to allow the virtual machines to warm-up. Out of the 11 remaining executions, the slowest one is ignored as well. The remaining runtimes are used to calculate average with the results presented below. The same applies to CPU times.

Memory consumption can be an issue as well. In both Node.js and Java the memory consumption varies during the test so it's not possible to provide a single number. On the other hand, we can at least get an idea about the memory consumption by measuring it in regular intervals when the benchmark is run using the following script (the script works for Java, similar one can be created for Node.js):
```bash
while sleep 1; do  ps -o pid,user,rss,args ax | grep java | grep -v grep; done
```

Using this script we can get [RSS](https://en.wikipedia.org/wiki/Resident_set_size) values as shown in the following table.


## SQL Server
Times are shown in the following table and charts.
Statement|Runtime Node.js [ms]|Runtime Java [ms]|Ratio of Node.js vs. Java|CPU time Node.js [ms]|CPU time Java [ms]|Ratio of CPU time of Node.js vs. Java
---------|--------------------|-----------------|-------------------------|---------------------|------------------|------------------------------------
Insert|968|256|3.78|1075|145|7.41
Update|1704|999|1.71|1075|53|20.28
Select|584|145|4.03|796|210|3.79

![Runtime](https://docs.google.com/spreadsheets/d/e/2PACX-1vQXyZpQW_Vr9obKpXbWkjdKUgTXPN4hq-zk5yiM1pbA5FXBnBWninDnzFszI4QVYueGr6DRea7-ulvZ/pubchart?oid=248742344&format=image)
![CPU time](https://docs.google.com/spreadsheets/d/e/2PACX-1vQXyZpQW_Vr9obKpXbWkjdKUgTXPN4hq-zk5yiM1pbA5FXBnBWninDnzFszI4QVYueGr6DRea7-ulvZ/pubchart?oid=1956263883&format=image)

Memory consumption is shown in the following table and chart.
Platform|Minimum [MB]|Maximum[MB]
--------|------------|-----------
Node.js|123|181
Java|102|189

![Memory consumption](https://docs.google.com/spreadsheets/d/e/2PACX-1vQXyZpQW_Vr9obKpXbWkjdKUgTXPN4hq-zk5yiM1pbA5FXBnBWninDnzFszI4QVYueGr6DRea7-ulvZ/pubchart?oid=1728331672&format=image)

## MongoDB
Times are shown in the following table and charts.
Statement|Runtime Node.js [ms]|Runtime Java [ms]|Ratio of Node.js vs. Java|CPU time Node.js [ms]|CPU time Java [ms]|Ratio of CPU time of Node.js vs. Java
---------|--------------------|-----------------|-------------------------|---------------------|------------------|------------------------------------
Insert|1032|798|1.29|519|310|1.67
Update|5847|5564|1.05|1090|1583|0.69
Select|498|203|2.45|472|178|2.65

![Runtime](https://docs.google.com/spreadsheets/d/e/2PACX-1vQXyZpQW_Vr9obKpXbWkjdKUgTXPN4hq-zk5yiM1pbA5FXBnBWninDnzFszI4QVYueGr6DRea7-ulvZ/pubchart?oid=2104507486&format=image) 
![CPU time](https://docs.google.com/spreadsheets/d/e/2PACX-1vQXyZpQW_Vr9obKpXbWkjdKUgTXPN4hq-zk5yiM1pbA5FXBnBWninDnzFszI4QVYueGr6DRea7-ulvZ/pubchart?oid=1867505239&format=image)

Memory consumption is shown in the following table and chart.
Platform|Minimum [MB]|Maximum[MB]
--------|------------|-----------
Node.js|108|156
Java|170|218

![Memory consumption](https://docs.google.com/spreadsheets/d/e/2PACX-1vQXyZpQW_Vr9obKpXbWkjdKUgTXPN4hq-zk5yiM1pbA5FXBnBWninDnzFszI4QVYueGr6DRea7-ulvZ/pubchart?oid=1615259715&format=image)

## SQL Server vs. MongoDB
Having the results for both databases allows us to compare the databases themselves as shown in the following charts.

![Runtime](https://docs.google.com/spreadsheets/d/e/2PACX-1vQXyZpQW_Vr9obKpXbWkjdKUgTXPN4hq-zk5yiM1pbA5FXBnBWninDnzFszI4QVYueGr6DRea7-ulvZ/pubchart?oid=1087019414&format=image)
![CPU time](https://docs.google.com/spreadsheets/d/e/2PACX-1vQXyZpQW_Vr9obKpXbWkjdKUgTXPN4hq-zk5yiM1pbA5FXBnBWninDnzFszI4QVYueGr6DRea7-ulvZ/pubchart?oid=1808123101&format=image)

# Summary
* SQL Server
  * Java can be about 2 to 4 times faster and use about 4 to 20 (!) times less CPU than Node.js.
  * Memory consumption of Java and Node.js is almost the same.
* MongoDB
  * Node.js is clearly better optimized for MongoDB than SQL Server. However, Java is still faster than Node.js in terms of runtime, but the difference is lower (about 1.05 to 2.5 times) than for SQL Server. Java also consumes about 2 timess less CPU time than Node.js except for updates, where Java consumes about 1.5 more CPU time than Node.js.
  * Java consumes about 1.5 times more memory than Node.js.
* SQL Server vs. MongoDB
 * SQL Server is clear winner here. You get, what you pay for.
 * While data reads are almost comparable (MongoDB is only about 40% slower), data modifications in MongoDB are many times (3 to 6) slower.

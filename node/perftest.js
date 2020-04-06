const sql = require('mssql');
 
function time() {
  return new Date().getTime();
}

function cpuTime() {
  const cpu = process.cpuUsage();
  return (cpu.user + cpu.system) / 1000;
}

function getAverage(data) {
  data.sort();
  let result = 0;
  for(let i = 0; i < data.length - 2; i++) result += data[i];
  return result / (data.length - 2);
} 
 
(async () => {
    try {
        // make sure that any items are correctly URL encoded in the connection string
        await sql.connect('mssql://pavel:password@db1.cams/master?encrypt=true');
        const totalRows = 100000;
        let first = true; 
        let inserts = [];
        let updates = [];
        let selects = [];
        let insertsCpu = [];
        let updatesCpu = [];
        let selectsCpu = [];
        for(let i = 0; i < 10; i++) {
          await sql.query`SET NOCOUNT ON`;
          await sql.query`truncate table tsdata`;
          
          // Insert
          let start = time();
          let startCpu = cpuTime();
          let transaction = new sql.Transaction();
          await transaction.begin();
          let table = new sql.Table('tsdata');
          table.columns.add('id', sql.Int, {nullable: false});
          table.columns.add('created', sql.DateTime2(0), {nullable: false});
          table.columns.add('value', sql.Real, {nullable: false});
          for(let i = 0; i < totalRows; i++) {
            table.rows.add(i, new Date(), i);
          }
          let request = new sql.Request(transaction);
          const inserted = await request.bulk(table);
          await transaction.commit();
          console.log(`Time to insert: ${time() - start} ${cpuTime() - startCpu}`);
          inserts.push(time() - start);
          insertsCpu.push(cpuTime() - startCpu);
          
          // Update
          start = time();
          startCpu = cpuTime();
          transaction = new sql.Transaction();
          await transaction.begin();
          if (first) {
            request = new sql.Request(transaction);
            await request.batch(`create table #tsdatatemp(id bigint not null, value float not null)`);
            first = false;
          }
          table = new sql.Table('#tsdatatemp');
          table.columns.add('id', sql.Int, {nullable: false});
          table.columns.add('value', sql.Real, {nullable: false});
          for(let i = 0; i < totalRows; i++) {
            table.rows.add(i, i);
          }
          request = new sql.Request(transaction);
          await request.bulk(table);
          request = new sql.Request(transaction);
          const updated = await request.query(`update d set d.value = s.value from tsdata d inner join #tsdatatemp s on d.id = s.id`);
          
          // Following code updates the values one by one, which is highly inefficient
          /*const ps = new sql.PreparedStatement();
          ps.input('value', sql.Real);
          ps.input('id', sql.Int);
          await ps.prepare('update tsdata set value = @value where id = @id');
          for(let i = 0; i < totalRows; i++) {
            await ps.execute({value: i + 1, id: i});
          } */
          await transaction.commit();
          console.log(`Time to update: ${time() - start} ${cpuTime() - startCpu}`);
          updates.push(time() - start);
          updatesCpu.push(cpuTime() - startCpu);
          
          // Select          
          start = time();
          startCpu = cpuTime();
          const result = await sql.query`select id, created, value from tsdata`;
          const rows = [];
          result.recordset.forEach(item => {
            rows.push({id: item.id, created: item.created, value: item.value});
          });
          console.log(`Selected ${rows.length} in ${time() - start} ${cpuTime() - startCpu}`);
          selects.push(time() - start);
          selectsCpu.push(cpuTime() - startCpu);
        }
        
        console.log(`Average insert: ${getAverage(inserts)} ${getAverage(insertsCpu)}`);
        console.log(`Average update: ${getAverage(updates)} ${getAverage(updatesCpu)}`);
        console.log(`Average select: ${getAverage(selects)} ${getAverage(selectsCpu)}`);
    } catch (err) {
        console.log(err);
    }
})()
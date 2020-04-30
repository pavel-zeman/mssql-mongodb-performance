const MongoClient = require('mongodb').MongoClient;
const url = "mongodb://db1.cams";
 
function time() {
  return new Date().getTime();
}

function cpuTime() {
  const cpu = process.cpuUsage();
  return (cpu.user + cpu.system) / 1000;
}

function getAverage(data) {
  data = data.slice(10);
  data.sort();
  let result = 0;
  for(let i = 0; i < data.length - 2; i++) result += data[i];
  return result / (data.length - 2);
} 
 
(async () => {
    try {
        // make sure that any items are correctly URL encoded in the connection string
        const client = await MongoClient.connect(url);
        const db = client.db("test");
        const collection = db.collection("tsdata");
        const totalRows = 100000;
        let first = true; 
        let inserts = [];
        let updates = [];
        let selects = [];
        let insertsCpu = [];
        let updatesCpu = [];
        let selectsCpu = [];
        
        for(let i = 0; i < 21; i++) {
          // Clear
          await collection.drop();
          global.gc();
          
          // Insert
          let start = time();
          let startCpu = cpuTime();
            
          let data = [];
          for(let i = 0; i < totalRows; i++) {
            data.push({_id: i, created: new Date(), value: i / 10.0});
          }
          await collection.insertMany(data);
          global.gc();
          console.log(`Time to insert: ${time() - start} ${cpuTime() - startCpu}`);
          inserts.push(time() - start);
          insertsCpu.push(cpuTime() - startCpu);

          
          // Update
          start = time();
          startCpu = cpuTime();
          let ops = [];
          for(let i = 0; i < totalRows; i++) {
            ops.push({updateOne: { filter: { _id: i}, update: {"$set": {value: i / 5.0}}}});
          }
          await collection.bulkWrite(ops);
          global.gc();
          
          console.log(`Time to update: ${time() - start} ${cpuTime() - startCpu}`);
          updates.push(time() - start);
          updatesCpu.push(cpuTime() - startCpu);
          
          // Select          
          start = time();
          startCpu = cpuTime();
          const result = await collection.find().toArray();
          const rows = [];
          result.forEach(item => rows.push({_id: item._id, created: item.created, value: item.value}));
          global.gc();

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
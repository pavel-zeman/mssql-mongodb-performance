import com.mongodb.MongoClient;
import com.mongodb.client.model.*;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;


public class PerfTestMongo {

    public static void main(String[] args) throws IOException {
        var totalRows = 100000;
        var inserts = new ArrayList<Long>();
        var batchUpdates = new ArrayList<Long>();
        var selects = new ArrayList<Long>();
        var insertsCpu = new ArrayList<Long>();
        var batchUpdatesCpu = new ArrayList<Long>();
        var selectsCpu = new ArrayList<Long>();

        // Register codec so that we can communicate using POJOs
        var pojoCodecProvider = PojoCodecProvider.builder().automatic(true).register(Item.class).build();
        var pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));
        var mongoClient = new MongoClient("db1.cams");
        var db = mongoClient.getDatabase("test").withCodecRegistry(pojoCodecRegistry);

        for (var it = 0; it < 21; it++) {
            // Remove all data
            var collection = db.getCollection("tsdata").withDocumentClass(Item.class);
            collection.drop();
            System.gc();

            // Insert rows
            var sw = StopWatch.startNew();
            var data = new ArrayList<Item>(totalRows);
            for (var i = 0; i < totalRows; i++) {
                var item = new Item(i, new Date(1000000), ((double) i / 10));
                data.add(item);
            }
            collection.insertMany(data, new InsertManyOptions().ordered(false));
            System.gc();
            System.out.println("Time to insert: " + sw.elapsed() + " " + sw.cpuElapsed());
            inserts.add(sw.elapsed());
            insertsCpu.add(sw.cpuElapsed());

            // Update rows using JDBC batch update
            sw = StopWatch.startNew();
            var writes = new ArrayList<WriteModel<Item>>(totalRows);
            for (var i = 0; i < totalRows; i++) {
                writes.add(new UpdateOneModel<>(Filters.eq("_id", i), Updates.set("value", (double) i / 5)));
            }
            collection.bulkWrite(writes, new BulkWriteOptions().ordered(false));
            System.gc();
            System.out.println("Time to batch update: " + sw.elapsed() + " " + sw.cpuElapsed());
            batchUpdates.add(sw.elapsed());
            batchUpdatesCpu.add(sw.cpuElapsed());

            // Select rows
            sw = StopWatch.startNew();
            var cursor = collection.find(Item.class);
            var rows = new ArrayList<Item>(totalRows);
            for (var item : cursor) {
                rows.add(item);
            }
            System.gc();
            System.out.println("Selected " + rows.size() + " in " + sw.elapsed() + " " + sw.cpuElapsed());
            selects.add(sw.elapsed());
            selectsCpu.add(sw.cpuElapsed());
        }

        System.out.println("Average insert: " + PerfTest.getAverage(inserts) + " " + PerfTest.getAverage(insertsCpu));
        System.out.println("Average batch update: " + PerfTest.getAverage(batchUpdates) + " " + PerfTest.getAverage(batchUpdatesCpu));
        System.out.println("Average select: " + PerfTest.getAverage(selects) + " " + PerfTest.getAverage(selectsCpu));

        System.in.read();

    }
}

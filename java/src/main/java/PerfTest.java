import com.microsoft.sqlserver.jdbc.ISQLServerBulkData;
import com.microsoft.sqlserver.jdbc.SQLServerBulkCopy;
import com.microsoft.sqlserver.jdbc.SQLServerConnection;

import java.io.IOException;
import java.sql.*;
import java.util.*;

class StopWatch {
    private long start;
    private long cpuStart;

    public StopWatch(long start) {
        this.start = start;
        this.cpuStart = getCpu();
    }

    private long getCpu() {
        return ProcessHandle.current().info().totalCpuDuration().get().toMillis();
    }

    public static StopWatch startNew() {
        return new StopWatch(System.currentTimeMillis());
    }

    public long elapsed() {
        return System.currentTimeMillis() - start;
    }

    public long cpuElapsed() {
        return getCpu() - cpuStart;
    }
}

class Item {
    private int id;
    private Time created;
    private double value;

    public Item(int id, Time created, double value) {
        this.id = id;
        this.created = created;
        this.value = value;
    }
}

class BulkData implements ISQLServerBulkData {

    private ArrayList<String> names = new ArrayList<>();
    private ArrayList<Integer> types = new ArrayList<>();
    private ArrayList<Integer> precisions = new ArrayList<>();
    private ArrayList<Integer> scales = new ArrayList<>();
    private ArrayList<Object[]> rows;
    private int currentRow = -1;

    public BulkData(int capacity) {
        this.rows = new ArrayList<>(capacity);
    }

    public void addColumn(String name, int type, int precision, int scale) {
        names.add(name);
        types.add(type);
        precisions.add(precision);
        scales.add(scale);
    }

    public void addRow(Object[] data) {
        rows.add(data);
    }

    @Override
    public Set<Integer> getColumnOrdinals() {
        var result = new HashSet<Integer>();
        for (var i = 0; i < names.size(); i++) {
            result.add(i + 1);
        }
        return result;
    }

    @Override
    public String getColumnName(int i) {
        return names.get(i - 1);
    }

    @Override
    public int getColumnType(int i) {
        return types.get(i - 1);
    }

    @Override
    public int getPrecision(int i) {
        return precisions.get(i - 1);
    }

    @Override
    public int getScale(int i) {
        return scales.get(i - 1);
    }

    @Override
    public Object[] getRowData() throws SQLException {
        return rows.get(currentRow);
    }

    @Override
    public boolean next() throws SQLException {
        currentRow++;
        return currentRow < rows.size();
    }
}


public class PerfTest {

    private static int getAverage(List<Long> data) {
        Collections.sort(data);
        var total = 0;
        for (var i = 0; i < data.size() - 1; i++) {
            total += data.get(i);
        }
        return total / (data.size() - 1);
    }


    public static void main(String[] args) throws IOException {
        // Create a variable for the connection string.
        String connectionUrl = "jdbc:sqlserver://db1.cams;databaseName=master;user=pavel;password=password;encrypt=true;trustServerCertificate=true;useBulkCopyForBatchInsert=true";
        var totalRows = 100000;
        var first = true;
        var inserts = new ArrayList<Long>();
        var updates = new ArrayList<Long>();
        var batchUpdates = new ArrayList<Long>();
        var selects = new ArrayList<Long>();
        var insertsCpu = new ArrayList<Long>();
        var updatesCpu = new ArrayList<Long>();
        var batchUpdatesCpu = new ArrayList<Long>();
        var selectsCpu = new ArrayList<Long>();


        try (Connection con = DriverManager.getConnection(connectionUrl)) {
            con.setAutoCommit(false);
            var con2 = (SQLServerConnection) con;
            System.out.println("Using bulk copy: " + con2.getUseBulkCopyForBatchInsert());
            for (var it = 0; it < 10; it++) {
                try (var statement = con.createStatement()) {
                    statement.execute("SET NOCOUNT ON");
                }

                // Remove all data
                try (var statement = con.createStatement()) {
                    statement.execute("truncate table tsdata");
                }

                // Insert rows
                var sw = StopWatch.startNew();
                // This code uses generic JDBC API to insert data - this API is quite slow compared to native BCP
//                try (var pstmt = con.prepareStatement("insert into tsdata(id, created, value) values(?, ?, ?)")) {
//                    // Add rows to a batch in a loop. Each iteration adds a new row
//                    for (var i = 0; i < totalRows; i++) {
//                        // Add each parameter to the row.
//                        pstmt.setInt(1, i);
//                        pstmt.setTime(2, new Time(1000000));
//                        pstmt.setDouble(3, i);
//                        pstmt.addBatch();
//                    }
//                    pstmt.executeBatch();
//                }
                try (var bc = new SQLServerBulkCopy(con)) {
                    var data = new BulkData(totalRows);
                    data.addColumn("id", Types.INTEGER, 0, 0);
                    data.addColumn("created", Types.DATE, 0, 0);
                    data.addColumn("value", Types.DOUBLE, 3, 10);
                    for (var i = 0; i < totalRows; i++) {
                        data.addRow(new Object[]{i, new Time(1000000), ((double) i / 10)});
                    }
                    bc.setDestinationTableName("tsdata");
                    bc.writeToServer(data);
                }
                con.commit();
                System.out.println("Time to insert: " + sw.elapsed() + " " + sw.cpuElapsed());
                inserts.add(sw.elapsed());
                insertsCpu.add(sw.cpuElapsed());


                // Update rows using JDBC batch update
                sw = StopWatch.startNew();
                try (var pstmt = con.prepareStatement("update tsdata set value = ? where id = ?")) {
                    for (var i = 0; i < totalRows; i++) {
                        // Add each parameter to the row.
                        pstmt.setDouble(1, i);
                        pstmt.setInt(2, i);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
                con.commit();
                System.out.println("Time to batch update: " + sw.elapsed() + " " + sw.cpuElapsed());
                batchUpdates.add(sw.elapsed());
                batchUpdatesCpu.add(sw.cpuElapsed());

                // Update rows using insert into temporary table and following batch update
                sw = StopWatch.startNew();
                if (first) {
                    try (var stmt = con.createStatement()) {
                        stmt.execute("create table #tsdatatemp(id bigint not null, value float not null)");
                    }
                    first = false;
                }
                try (var bc = new SQLServerBulkCopy(con)) {
                    var data = new BulkData(totalRows);
                    data.addColumn("id", Types.INTEGER, 0, 0);
                    data.addColumn("value", Types.DOUBLE, 3, 10);
                    for (var i = 0; i < totalRows; i++) {
                        data.addRow(new Object[]{i, ((double) i / 10) + 1});
                    }
                    bc.setDestinationTableName("#tsdatatemp");
                    bc.writeToServer(data);
                }
                try (var stmt = con.createStatement()) {
                    stmt.execute("update d set d.value = s.value from tsdata d inner join #tsdatatemp s on d.id = s.id");
                }
                con.commit();
                System.out.println("Time to update: " + sw.elapsed() + " " + sw.cpuElapsed());
                updates.add(sw.elapsed());
                updatesCpu.add(sw.cpuElapsed());


                // Select rows
                sw = StopWatch.startNew();
                var rows = new ArrayList<Item>(totalRows);
                try (var statement = con.createStatement()) {
                    var rs = statement.executeQuery("select id, created, value from tsdata");
                    while (rs.next()) {
                        rows.add(new Item(rs.getInt(1), rs.getTime(2), rs.getDouble(3)));
                    }
                }
                System.out.println("Selected " + rows.size() + " in " + sw.elapsed() + " " + sw.cpuElapsed());
                selects.add(sw.elapsed());
                selectsCpu.add(sw.cpuElapsed());
            }
        }
        // Handle any errors that may have occurred.
        catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("Average insert: " + getAverage(inserts) + " " + getAverage(insertsCpu));
        System.out.println("Average update: " + getAverage(updates) + " " + getAverage(updatesCpu));
        System.out.println("Average batch update: " + getAverage(batchUpdates) + " " + getAverage(batchUpdatesCpu));
        System.out.println("Average select: " + getAverage(selects) + " " + getAverage(selectsCpu));

        System.in.read();

    }
}

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PerfTest {

    public static int getAverage(List<Long> data) {
        var data2 = new ArrayList<Long>();
        for(var i = 10; i < data.size(); i++) {
            data2.add(data.get(i));
        }
        data = data2;
        Collections.sort(data);
        var total = 0;
        for (var i = 0; i < data.size() - 1; i++) {
            total += data.get(i);
        }
        return total / (data.size() - 1);
    }
}

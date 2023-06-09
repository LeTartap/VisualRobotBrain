package robotbrain;

import java.util.Map;

public interface ValueUpdateListener {
    void onValuesUpdated(Map<String, String> updatedValues);
}
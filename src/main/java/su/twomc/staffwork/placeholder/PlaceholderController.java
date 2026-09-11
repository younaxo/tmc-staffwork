package su.twomc.staffwork.placeholder;

import java.util.Map;
import su.twomc.staffwork.model.WorkStatus;

public interface PlaceholderController {
    PlaceholderController NONE = colors -> {};

    void updateColors(Map<WorkStatus, String> colors);
}

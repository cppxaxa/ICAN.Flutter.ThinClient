package com.ican.thinclient006;

import java.util.ArrayList;
import java.util.List;

public class HotwordDetectionEvent {
    private List<HotwordListener> listeners = new ArrayList<HotwordListener>();

    public void addListener(HotwordListener toAdd) {
        listeners.add(toAdd);
    }

    public void PublishHotword(String hotword) {
        for (HotwordListener hl : listeners)
            hl.OnDetection(hotword);
    }
}

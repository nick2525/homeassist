package com.axzae.homeassistant.shared;

import android.content.Context;

import com.axzae.homeassistant.model.HomeAssistantServer;
import com.axzae.homeassistant.model.rest.CallServiceRequest;

import androidx.fragment.app.FragmentManager;

public interface EntityProcessInterface {
    void callService(final String domain, final String service, CallServiceRequest serviceRequest);

    FragmentManager getSupportFragmentManager();

    HomeAssistantServer getServer();

    Context getActivityContext();

    void showToast(String message);
}

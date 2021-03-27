package com.axzae.homeassistant.shared;

import com.axzae.homeassistant.model.Entity;

public interface EntityInterface {
    /**
     * @param entity
     * @return true to indicate action is consumed.
     */
    //boolean onEntitySelected(Entity entity);

    void onEntityUpperViewClick(EntityAdapter.EntityTileViewHolder viewHolder, Entity entity);

    boolean onEntityUpperViewLongClick(EntityAdapter.EntityTileViewHolder viewHolder, Entity entity);

    //void callService(final String domain, final String service, CallServiceRequest serviceRequest);
}

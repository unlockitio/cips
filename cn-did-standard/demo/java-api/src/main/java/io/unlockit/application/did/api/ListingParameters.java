package io.unlockit.application.did.api;

import io.unlockit.application.did.manager.DidListingManager;
import jakarta.ws.rs.QueryParam;

public class ListingParameters {
    @QueryParam("state") public String state;
    @QueryParam("createdFrom") public String createdFrom;
    @QueryParam("createdUntil") public String createdUntil;
    @QueryParam("archivedFrom") public String archivedFrom;
    @QueryParam("archivedUntil") public String archivedUntil;
    @QueryParam("page") public String page;
    @QueryParam("pageSize") public String pageSize;
    @QueryParam("nextPageToken") public String nextPageToken;

    public DidListingManager.Listing list(DidListingManager manager, String resource, String partyId) {
        return manager.list(resource, state, partyId, createdFrom, createdUntil, archivedFrom, archivedUntil, page, pageSize, nextPageToken);
    }
}

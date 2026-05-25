package org.jztrmnkl.filterservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Event {

    private String eventId;
    private String eventType;
    private String cookieId;
    private String clientTimestamp;
    private String receivedAt;
    private String userAgent;
    private String ip;
    private String placementId;
    private String referrer;

    public String getEventId()          { return eventId; }
    public String getEventType()        { return eventType; }
    public String getCookieId()         { return cookieId; }
    public String getClientTimestamp()  { return clientTimestamp; }
    public String getReceivedAt()       { return receivedAt; }
    public String getUserAgent()        { return userAgent; }
    public String getIp()               { return ip; }
    public String getPlacementId()      { return placementId; }
    public String getReferrer()         { return referrer; }

    public void setEventId(String eventId)                  { this.eventId = eventId; }
    public void setEventType(String eventType)              { this.eventType = eventType; }
    public void setCookieId(String cookieId)                { this.cookieId = cookieId; }
    public void setClientTimestamp(String clientTimestamp)  { this.clientTimestamp = clientTimestamp; }
    public void setReceivedAt(String receivedAt)            { this.receivedAt = receivedAt; }
    public void setUserAgent(String userAgent)              { this.userAgent = userAgent; }
    public void setIp(String ip)                            { this.ip = ip; }
    public void setPlacementId(String placementId)          { this.placementId = placementId; }
    public void setReferrer(String referrer)                { this.referrer = referrer; }
}

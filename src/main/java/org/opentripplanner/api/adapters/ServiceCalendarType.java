package org.opentripplanner.api.adapters;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.calendar.ServiceCalendar;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "Calendar")
public class ServiceCalendarType {

    public ServiceCalendarType(FeedScopedId serviceId, int monday, int tuesday, int wednesday,
                               int thursday, int friday, int saturday, int sunday, long startDate, long endDate) {
        this.serviceId = serviceId;
        this.monday = monday;
        this.tuesday = tuesday;
        this.wednesday = wednesday;
        this.thursday = thursday;
        this.friday = friday;
        this.saturday = saturday;
        this.sunday = sunday;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public ServiceCalendarType(ServiceCalendar arg) {
        this.serviceId = arg.getServiceId();
        this.monday = arg.getMonday();
        this.tuesday = arg.getTuesday();
        this.wednesday = arg.getWednesday();
        this.thursday = arg.getThursday();
        this.friday = arg.getFriday();
        this.saturday = arg.getSaturday();
        this.sunday = arg.getSunday();
        this.startDate = arg.getPeriod().getStart().getAsDate().getTime();
        this.endDate = arg.getPeriod().getEnd().getAsDate().getTime();
    }

    public ServiceCalendarType() {
    }

    @XmlJavaTypeAdapter(AgencyAndIdAdapter.class)
    @JsonSerialize
    FeedScopedId serviceId;

    @XmlAttribute
    @JsonSerialize
    Integer monday;

    @XmlAttribute
    @JsonSerialize
    Integer tuesday;

    @XmlAttribute
    @JsonSerialize
    Integer wednesday;

    @XmlAttribute
    @JsonSerialize
    Integer thursday;

    @XmlAttribute
    @JsonSerialize
    Integer friday;

    @XmlAttribute
    @JsonSerialize
    Integer saturday;

    @XmlAttribute
    @JsonSerialize
    Integer sunday;

    @XmlAttribute
    @JsonSerialize
    Long startDate;

    @XmlAttribute
    @JsonSerialize
    Long endDate;

}
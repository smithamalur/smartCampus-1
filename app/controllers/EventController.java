package controllers;


import com.avaje.ebean.Ebean;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import models.database.*;
import play.data.DynamicForm;
import play.data.FormFactory;
import play.libs.Json;
import play.libs.ws.*;
import play.mvc.Controller;
import play.mvc.Result;
import references.Constants;
import scala.util.parsing.json.JSONObject;
import views.html.*;

import javax.persistence.PersistenceException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletionStage;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/**
 * Created by mallem on 3/19/16.
 */
public class EventController extends Controller {

    @Inject
    FormFactory formFactory;
    @Inject
    WSClient ws;

    public Result getEventsByLocation(String location) {
        List<Event> eventList = Ebean.find(Event.class).where().ieq("location", location).findList();
        return ok(eventList.toString());
    }

    public Result getEventsByCategories() {
        DynamicForm form = formFactory.form().bindFromRequest();
        String[] categories = form.get("categories").split(",");

        HashSet<Event> events = new HashSet<>();

        for (String category : categories) {
            events.addAll(Ebean.find(Event.class).where().ieq("category", category).ieq("is_active", "1").findList());
        }

        return ok(events.toString());
    }

    public Result getEventPage(String deviceId) {
        boolean isAdmin = false;
        User user = Ebean.find(User.class).where().ieq("device_id", deviceId).findUnique();
        List<Category> categories = Ebean.find(Category.class).findList();
        if (user != null) {
            String[] roles = user.getRole().split(",");
            for (int i = 0; i < roles.length; i++) {
                if (roles[i].equalsIgnoreCase("admin")) {
                    isAdmin = true;
                    break;
                }
            }
            if (isAdmin) {
                List<Location> locations = Ebean.find(Location.class).findList();
                return ok(createEvent.render(locations, categories, deviceId));
            } else {
                return ok(nopermission.render());
            }
        } else {
            return ok(createUser.render(deviceId, categories));
        }
    }

    /*
        TODO : Fix start/end time
        TODO : prof suggests that we should show events even though the user is not subscribed to them based on timings (e.g., say showing food related events to most of the users at 4 pm.) Make a plan of action for the same.
     */
    public Result createEvent() {
        Map<String, String[]> form = request().body().asFormUrlEncoded();
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
        try {
            Date startTime = new Date(format.parse(form.get("startTime")[0]).getTime());
            Date endTime = new Date(format.parse(form.get("endTime")[0]).getTime());

            Event event = Event.builder()
                    .name(form.get("name")[0])
                    .description(form.get("description")[0])
                    .externalLink(form.get("externalLink")[0])
                    .category(getCategories(form.get("categories")))
                    .startTime(startTime)
                    .endTime(endTime)
                    .isActive(Boolean.valueOf(form.get("isActive")[0]))
                    .location(form.get("location")[0])
                    .beacons(getBeaconsForEvent(form.get("beaconLocations")))
                    .createdBy(form.get("createdBy")[0])
                    .build();
            event.save();

        } catch (PersistenceException p) {
            return badRequest("Event Already Exists");
        } catch (ParseException e) {
            return badRequest("Bad Request From UI");
        }
        return redirect("/events/" + form.get("createdBy")[0]);
    }

    private String getCategories(String[] categories) {
        String result = "";
        if (categories != null) {
            for (String category : categories) {
                result += category + ",";
            }
        }
        return result;
    }

    private List<Beacon> getBeaconsForEvent(String[] locations) {
        List<Beacon> beacons = new ArrayList<>();
        if (locations != null) {
            for (String location : locations) {
                beacons.addAll(Ebean.find(Location.class).where().ieq("name", location).findUnique().getBeacons());
            }
        }
        return beacons;
    }

    public Result getEventsByUser(String user) {
        List<Event> eventList = Ebean.find(Event.class).where().ieq("createdBy", user).findList();
        return ok(events.render(eventList));
    }

    public Result testUI() {
        return ok(main.render("Test", null));
    }

    public Result getRecommendedEvents(String deviceId) throws IOException {
        String result = "";
        URL url = new URL(Constants.Urls.ML_URL + deviceId + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result = result + line;
        }
        rd.close();
        JsonNode json = Json.parse(result);
        //this will have the json variable and convert this into eventIds
        json.get(Constants.KeyWords.ML_EVENT_IDS);

        List<String> eventIds = new ArrayList<>();
        return ok(events.render(Ebean.find(Event.class).where().in("id", eventIds).findList()));
    }

    public Result getStarredEvents(String deviceId) {
        //todo: return starred events for the user
        return ok();
    }

}

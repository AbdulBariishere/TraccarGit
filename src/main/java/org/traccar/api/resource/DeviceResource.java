/*
 * Copyright 2015 - 2018 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api.resource;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.traccar.Context;
import org.traccar.api.BaseObjectResource;
import org.traccar.database.DataManager;
import org.traccar.database.DeviceManager;
import org.traccar.helper.LogAction;
import org.traccar.model.Device;
import org.traccar.model.DeviceAccumulators;
import org.traccar.model.Driver;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.sql.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Path("devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeviceResource extends BaseObjectResource<Device> {

    public static final String DRIVER = "com.mysql.jdbc.Driver";
    public static final String URL = "jdbc:mysql://localhost:3306/traccar";
    public static final String USERNAME = "root";
    public static final String PASSWORD = "root";
    public PreparedStatement preparedStatement;
    public Connection connection;
    public ResultSet resultSet;

    public DeviceResource() {
        super(Device.class);
    }

    @GET
    public Collection<Device> get(
            @QueryParam("all") boolean all, @QueryParam("userId") long userId,
            @QueryParam("uniqueId") List<String> uniqueIds,
            @QueryParam("id") List<Long> deviceIds) throws SQLException {
        DeviceManager deviceManager = Context.getDeviceManager();
        Set<Long> result = null;
        if (all) {
            if (Context.getPermissionsManager().getUserAdmin(getUserId())) {
                result = deviceManager.getAllItems();
            } else {
                Context.getPermissionsManager().checkManager(getUserId());
                result = deviceManager.getManagedItems(getUserId());
            }
        } else if (uniqueIds.isEmpty() && deviceIds.isEmpty()) {
            if (userId == 0) {
                userId = getUserId();
            }
            Context.getPermissionsManager().checkUser(getUserId(), userId);
            if (Context.getPermissionsManager().getUserAdmin(getUserId())) {
                result = deviceManager.getAllUserItems(userId);
            } else {
                result = deviceManager.getUserItems(userId);
            }
        } else {
            result = new HashSet<>();
            for (String uniqueId : uniqueIds) {
                Device device = deviceManager.getByUniqueId(uniqueId);
                Context.getPermissionsManager().checkDevice(getUserId(), device.getId());
                result.add(device.getId());
            }
            for (Long deviceId : deviceIds) {
                Context.getPermissionsManager().checkDevice(getUserId(), deviceId);
                result.add(deviceId);
            }
        }
        return deviceManager.getItems(result);
    }

    @Path("{id}/accumulators")
    @PUT
    public Response updateAccumulators(DeviceAccumulators entity) throws SQLException {
        if (!Context.getPermissionsManager().getUserAdmin(getUserId())) {
            Context.getPermissionsManager().checkManager(getUserId());
            Context.getPermissionsManager().checkPermission(Device.class, getUserId(), entity.getDeviceId());
        }
        Context.getDeviceManager().resetDeviceAccumulators(entity);
        LogAction.resetDeviceAccumulators(getUserId(), entity.getDeviceId());
        return Response.noContent().build();
    }

    @Path("/all")
    @GET
    public JSONArray getalldevicesdata() throws SQLException {
        JSONArray jsonArray = new JSONArray();
        String result = "";
        String sql = "SELECT tc_devices.name,tc_position_objects.servertime,tc_position_objects.speed,tc_position_objects.attributes  FROM traccar.tc_position_objects inner join tc_devices where traccar.tc_position_objects.deviceid =traccar.tc_devices.id";
        try {
            Class.forName(DRIVER);
            Connection connection = DriverManager.getConnection(
                    URL, USERNAME, PASSWORD);
            ResultSet resultSet = connection.prepareStatement(sql).executeQuery();
            while (resultSet.next()) {
                JSONObject jsonObject1 = new JSONObject();
                jsonObject1.put("name", resultSet.getString(1));
                jsonObject1.put("lastUpdatedTime", resultSet.getString(2));
                jsonObject1.put("speed", resultSet.getDouble(3));
                jsonObject1.put("attributes", resultSet.getString(4));
                jsonArray.add(jsonObject1);
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return jsonArray;
    }

    @Path("/statuscount")
    @GET
    public JSONArray getallstatuscount() throws SQLException {
        JSONArray jsonArray = new JSONArray();
        String TOTAL, RUNNING, IDLE, STOP, NODATA, INACTIVE;
        TOTAL = "SELECT count(*) FROM traccar.tc_devices";
        RUNNING = "SELECT count(*) FROM traccar.tc_position_objects inner join traccar.tc_devices \n" +
                "where \n" +
                "tc_position_objects.deviceid=tc_devices.id AND tc_position_objects.speed > 0.0 ";

        IDLE = "SELECT count(*) FROM traccar.tc_position_objects inner join traccar.tc_devices \n" +
                "where \n" +
                "tc_position_objects.deviceid=tc_devices.id AND tc_position_objects.speed = 0.0 AND tc_position_objects.ignition=true AND tc_position_objects.network != 'null'";

        STOP = "SELECT count(*) FROM traccar.tc_position_objects inner join traccar.tc_devices \n" +
                "where \n" +
                "tc_position_objects.deviceid=tc_devices.id AND tc_position_objects.speed = 0.0 AND tc_position_objects.ignition=false AND tc_position_objects.network != 'null'";

        INACTIVE = "SELECT count(*) FROM traccar.tc_position_objects inner join traccar.tc_devices \n" +
                "where \n" +
                "tc_position_objects.deviceid=tc_devices.id AND tc_position_objects.network = 'null'";

        NODATA = "SELECT DISTINCT count(tc_devices.id) FROM tc_devices\n" +
                "  WHERE NOT EXISTS (SELECT * FROM tc_position_objects\n" +
                "                    WHERE tc_position_objects.deviceid = tc_devices.id)";
        try {
            Class.forName(DRIVER);
            Connection connection = DriverManager.getConnection(
                    URL, USERNAME, PASSWORD);
            JSONObject jsonObject = new JSONObject();
            resultSet = connection.prepareStatement(TOTAL).executeQuery();
            while (resultSet.next()) {

                jsonObject.put("TOTAL", resultSet.getString(1));

            }
            resultSet = connection.prepareStatement(RUNNING).executeQuery();
            while (resultSet.next()) {

                jsonObject.put("RUNNING", resultSet.getString(1));

            }
            resultSet = connection.prepareStatement(IDLE).executeQuery();
            while (resultSet.next()) {

                jsonObject.put("IDLE", resultSet.getString(1));

            }
            resultSet = connection.prepareStatement(STOP).executeQuery();
            while (resultSet.next()) {

                jsonObject.put("STOP", resultSet.getString(1));

            }
            resultSet = connection.prepareStatement(INACTIVE).executeQuery();
            while (resultSet.next()) {

                jsonObject.put("INACTIVE", resultSet.getString(1));

            }

            resultSet = connection.prepareStatement(NODATA).executeQuery();
            while (resultSet.next()) {
                jsonObject.put("NODATA", resultSet.getString(1));

            }
          jsonArray.add(jsonObject);

        } catch (Exception ex) {
            System.out.println("DeviceResource -> Status Count Exception =" + ex.getMessage());
        }
        return jsonArray;
    }

    @Path("/running")
    @GET
    public JSONArray getrunning() throws SQLException {
        JSONArray jsonArray = new JSONArray();
        String RUNNING;
        RUNNING = "SELECT * FROM traccar.tc_position_objects inner join traccar.tc_devices \n" +
                "where \n" +
                "tc_position_objects.deviceid=tc_devices.id AND tc_position_objects.speed > 0.0 ";
        try {
            Class.forName(DRIVER);
            Connection connection = DriverManager.getConnection(
                    URL, USERNAME, PASSWORD);
            resultSet = connection.prepareStatement(RUNNING).executeQuery();
            while (resultSet.next()) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", resultSet.getInt(1));
                jsonObject.put("protocol", resultSet.getString(2));
                jsonObject.put("deviceid", resultSet.getInt(3));
                jsonObject.put("servertime", resultSet.getString(4));
                jsonObject.put("devicetime", resultSet.getString(5));
                jsonObject.put("fixtime", resultSet.getString(6));
                jsonObject.put("latitude", resultSet.getDouble(8));
                jsonObject.put("longitude", resultSet.getDouble(9));
                jsonObject.put("altitude", resultSet.getFloat(10));
                jsonObject.put("speed", resultSet.getFloat(11));
                jsonObject.put("course", resultSet.getFloat(12));
                jsonObject.put("address", resultSet.getString(13));
                jsonObject.put("attributes", resultSet.getString(14));
                jsonObject.put("accuracy", resultSet.getDouble(15));
                jsonObject.put("network", resultSet.getString(16));
                jsonObject.put("distance", resultSet.getDouble(17));
                jsonObject.put("totalDistance", resultSet.getDouble(18));
                jsonObject.put("ignition", resultSet.getString(19));
                jsonObject.put("name",resultSet.getString(21));
                jsonObject.put("uniqueid",resultSet.getString(22));
                jsonArray.add(jsonObject);
            }
        } catch (Exception ex) {
            System.out.println("DeviceResource -> Running Exception =" + ex.getMessage());
        }
        return jsonArray;
    }
    @Path("/stop")
    @GET
    public JSONArray getstop() throws SQLException {
        JSONArray jsonArray = new JSONArray();
        String STOP;
        STOP = "SELECT * FROM traccar.tc_position_objects inner join traccar.tc_devices \n" +
                "where \n" +
                "tc_position_objects.deviceid=tc_devices.id AND tc_position_objects.speed = 0.0 AND tc_position_objects.ignition=false AND tc_position_objects.network != 'null'";
        try {
            Class.forName(DRIVER);
            Connection connection = DriverManager.getConnection(
                    URL, USERNAME, PASSWORD);
            resultSet = connection.prepareStatement(STOP).executeQuery();
            while (resultSet.next()) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", resultSet.getInt(1));
                jsonObject.put("protocol", resultSet.getString(2));
                jsonObject.put("deviceid", resultSet.getInt(3));
                jsonObject.put("servertime", resultSet.getString(4));
                jsonObject.put("devicetime", resultSet.getString(5));
                jsonObject.put("fixtime", resultSet.getString(6));
                jsonObject.put("latitude", resultSet.getDouble(8));
                jsonObject.put("longitude", resultSet.getDouble(9));
                jsonObject.put("altitude", resultSet.getFloat(10));
                jsonObject.put("speed", resultSet.getFloat(11));
                jsonObject.put("course", resultSet.getFloat(12));
                jsonObject.put("address", resultSet.getString(13));
                jsonObject.put("attributes", resultSet.getString(14));
                jsonObject.put("accuracy", resultSet.getDouble(15));
                jsonObject.put("network", resultSet.getString(16));
                jsonObject.put("distance", resultSet.getDouble(17));
                jsonObject.put("totalDistance", resultSet.getDouble(18));
                jsonObject.put("ignition", resultSet.getString(19));
                jsonObject.put("name",resultSet.getString(21));
                jsonObject.put("uniqueid",resultSet.getString(22));
                jsonArray.add(jsonObject);
            }
        } catch (Exception ex) {
            System.out.println("DeviceResource -> STOP Exception =" + ex.getMessage());
        }
        return jsonArray;
    }

    @Path("/idle")
    @GET
    public JSONArray getidle() throws SQLException {
        JSONArray jsonArray = new JSONArray();
        String IDLE;
        IDLE = "SELECT * FROM traccar.tc_position_objects inner join traccar.tc_devices \n" +
                "where \n" +
                "tc_position_objects.deviceid=tc_devices.id AND tc_position_objects.speed = 0.0 AND tc_position_objects.ignition=true AND tc_position_objects.network != 'null'";

        try {
            Class.forName(DRIVER);
            Connection connection = DriverManager.getConnection(
                    URL, USERNAME, PASSWORD);
            resultSet = connection.prepareStatement(IDLE).executeQuery();
            while (resultSet.next()) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", resultSet.getInt(1));
                jsonObject.put("protocol", resultSet.getString(2));
                jsonObject.put("deviceid", resultSet.getInt(3));
                jsonObject.put("servertime", resultSet.getString(4));
                jsonObject.put("devicetime", resultSet.getString(5));
                jsonObject.put("fixtime", resultSet.getString(6));
                jsonObject.put("latitude", resultSet.getDouble(8));
                jsonObject.put("longitude", resultSet.getDouble(9));
                jsonObject.put("altitude", resultSet.getFloat(10));
                jsonObject.put("speed", resultSet.getFloat(11));
                jsonObject.put("course", resultSet.getFloat(12));
                jsonObject.put("address", resultSet.getString(13));
                jsonObject.put("attributes", resultSet.getString(14));
                jsonObject.put("accuracy", resultSet.getDouble(15));
                jsonObject.put("network", resultSet.getString(16));
                jsonObject.put("distance", resultSet.getDouble(17));
                jsonObject.put("totalDistance", resultSet.getDouble(18));
                jsonObject.put("ignition", resultSet.getString(19));
                jsonObject.put("name",resultSet.getString(21));
                jsonObject.put("uniqueid",resultSet.getString(22));
                jsonArray.add(jsonObject);
            }
        } catch (Exception ex) {
            System.out.println("DeviceResource -> IDLE Exception =" + ex.getMessage());
        }
        return jsonArray;
    }
    @Path("/inactive")
    @GET
    public JSONArray getinactive() throws SQLException {
        JSONArray jsonArray = new JSONArray();
        String INACTIVE;
        INACTIVE = "SELECT * FROM traccar.tc_position_objects inner join traccar.tc_devices \n" +
                "where \n" +
                "tc_position_objects.deviceid=tc_devices.id AND tc_position_objects.network = 'null'";

        try {
            Class.forName(DRIVER);
            Connection connection = DriverManager.getConnection(
                    URL, USERNAME, PASSWORD);
            resultSet = connection.prepareStatement(INACTIVE).executeQuery();
            while (resultSet.next()) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", resultSet.getInt(1));
                jsonObject.put("protocol", resultSet.getString(2));
                jsonObject.put("deviceid", resultSet.getInt(3));
                jsonObject.put("servertime", resultSet.getString(4));
                jsonObject.put("devicetime", resultSet.getString(5));
                jsonObject.put("fixtime", resultSet.getString(6));
                jsonObject.put("latitude", resultSet.getDouble(8));
                jsonObject.put("longitude", resultSet.getDouble(9));
                jsonObject.put("altitude", resultSet.getFloat(10));
                jsonObject.put("speed", resultSet.getFloat(11));
                jsonObject.put("course", resultSet.getFloat(12));
                jsonObject.put("address", resultSet.getString(13));
                jsonObject.put("attributes", resultSet.getString(14));
                jsonObject.put("accuracy", resultSet.getDouble(15));
                jsonObject.put("network", resultSet.getString(16));
                jsonObject.put("distance", resultSet.getDouble(17));
                jsonObject.put("totalDistance", resultSet.getDouble(18));
                jsonObject.put("ignition", resultSet.getString(19));
                jsonObject.put("name",resultSet.getString(21));
                jsonObject.put("uniqueid",resultSet.getString(22));
                jsonArray.add(jsonObject);
            }
        } catch (Exception ex) {
            System.out.println("DeviceResource -> INACTIVE Exception =" + ex.getMessage());
        }
        return jsonArray;
    }
    @Path("/nodata")
    @GET
    public JSONArray getnodata() throws SQLException {
        JSONArray jsonArray = new JSONArray();
        String NODATA;
        NODATA = "SELECT DISTINCT * FROM tc_devices\n" +
                "  WHERE NOT EXISTS (SELECT * FROM tc_position_objects\n" +
                "                    WHERE tc_position_objects.deviceid = tc_devices.id)";

        try {
            Class.forName(DRIVER);
            Connection connection = DriverManager.getConnection(
                    URL, USERNAME, PASSWORD);
            resultSet = connection.prepareStatement(NODATA).executeQuery();
            while (resultSet.next()) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", resultSet.getInt(1));
                jsonObject.put("name", resultSet.getString(2));
                jsonObject.put("uniqueid", resultSet.getString(3));
                jsonObject.put("attributes", resultSet.getString(7));
                jsonArray.add(jsonObject);
            }
        } catch (Exception ex) {
            System.out.println("DeviceResource -> NODATA Exception =" + ex.getMessage());
        }
        return jsonArray;
    }
    @Path("/specificdevice")
    @GET
    public JSONArray getspecificdevice(@QueryParam("id") int id)throws SQLException{
        JSONArray jsonArray = new JSONArray();
        String Query ="select * from traccar.tc_position_objects inner join traccar.tc_devices where "+
        "tc_position_objects.deviceid=tc_devices.id AND deviceid="+(id)+"";
        try {
            Class.forName(DRIVER);
            Connection connection = DriverManager.getConnection(
                    URL, USERNAME, PASSWORD);
            resultSet = connection.prepareStatement(Query).executeQuery();
            while (resultSet.next()) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", resultSet.getInt(1));
                jsonObject.put("protocol", resultSet.getString(2));
                jsonObject.put("deviceid", resultSet.getInt(3));
                jsonObject.put("servertime", resultSet.getString(4));
                jsonObject.put("devicetime", resultSet.getString(5));
                jsonObject.put("fixtime", resultSet.getString(6));
                jsonObject.put("latitude", resultSet.getDouble(8));
                jsonObject.put("longitude", resultSet.getDouble(9));
                jsonObject.put("altitude", resultSet.getFloat(10));
                jsonObject.put("speed", resultSet.getFloat(11));
                jsonObject.put("course", resultSet.getFloat(12));
                jsonObject.put("address", resultSet.getString(13));
                jsonObject.put("attributes", resultSet.getString(14));
                jsonObject.put("accuracy", resultSet.getDouble(15));
                jsonObject.put("network", resultSet.getString(16));
                jsonObject.put("distance", resultSet.getDouble(17));
                jsonObject.put("totalDistance", resultSet.getDouble(18));
                jsonObject.put("ignition", resultSet.getString(19));
                jsonObject.put("name",resultSet.getString(21));
                jsonObject.put("uniqueid",resultSet.getString(22));
                jsonArray.add(jsonObject);
            }
        } catch (Exception ex) {
            System.out.println("DeviceResource -> SpecificDevice Exception =" + ex.getMessage());
        }


        return jsonArray;
    }
}
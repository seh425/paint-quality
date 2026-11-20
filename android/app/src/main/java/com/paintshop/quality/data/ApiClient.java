package com.paintshop.quality.data;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.paintshop.quality.model.DraftVehicle;
import com.paintshop.quality.model.InspectionRecord;
import com.paintshop.quality.model.User;
import com.paintshop.quality.model.VehicleModel;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class ApiClient {
    public interface Callback<T> {
        void onSuccess(T data);

        void onError(String message);
    }

    public static class AuthResponse {
        public String token;
        public User user;
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static ApiClient instance;

    public static synchronized ApiClient get(Context context) {
        if (instance == null) {
            instance = new ApiClient(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SessionManager session;

    private ApiClient(Context context) {
        this.context = context;
        session = new SessionManager(context);
    }

    private String url(String path) {
        return session.getBaseUrl() + (path.startsWith("/") ? path.substring(1) : path);
    }

    private <T> void request(String method, String path, Object body, Type type,
                             Callback<T> callback) {
        Request.Builder builder = new Request.Builder().url(url(path));
        String token = session.getToken();
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("GET".equals(method)) {
            builder.get();
        } else if ("DELETE".equals(method)) {
            builder.delete();
        } else {
            Object payload = body == null ? new JsonObject() : body;
            builder.method(method, RequestBody.create(gson.toJson(payload), JSON));
        }
        enqueue(builder.build(), type, callback);
    }

    private <T> void enqueue(Request request, Type type, Callback<T> callback) {
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                deliverError(callback, "无法连接服务器，请检查服务器地址、服务端是否启动以及手机与服务器网络是否互通");
            }

            @Override
            public void onResponse(Call call, Response response) {
                String responseBody;
                try {
                    responseBody = response.body() == null ? "" : response.body().string();
                } catch (IOException error) {
                    deliverError(callback, "读取服务器响应失败：" + error.getMessage());
                    return;
                }
                if (!response.isSuccessful()) {
                    deliverError(callback, serverMessage(responseBody, response.code()));
                    return;
                }
                try {
                    T value = null;
                    if (type != null && type != Void.class && !responseBody.trim().isEmpty()) {
                        value = gson.fromJson(responseBody, type);
                    }
                    deliverSuccess(callback, value);
                } catch (RuntimeException error) {
                    deliverError(callback, "服务器返回的数据无法解析");
                }
            }
        });
    }

    private String serverMessage(String body, int status) {
        if (body != null && !body.trim().isEmpty()) {
            try {
                JsonObject object = JsonParser.parseString(body).getAsJsonObject();
                if (object.has("message")) {
                    return object.get("message").getAsString();
                }
                if (object.has("error")) {
                    return object.get("error").getAsString();
                }
            } catch (RuntimeException ignored) {
                // Use the HTTP status when the response is not a JSON error object.
            }
        }
        return "请求失败（HTTP " + status + "）";
    }

    private <T> void deliverSuccess(Callback<T> callback, T value) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onSuccess(value);
            }
        });
    }

    private <T> void deliverError(Callback<T> callback, String message) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onError(message);
            }
        });
    }

    private Map<String, Object> fields(String inspectorName, String area) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inspectorName", inspectorName);
        body.put("area", area);
        return body;
    }

    public void register(String inspectorName, String area, Callback<AuthResponse> callback) {
        request("POST", "/api/auth/register", fields(inspectorName, area),
                AuthResponse.class, callback);
    }

    public void login(String inspectorName, String area, Callback<AuthResponse> callback) {
        request("POST", "/api/auth/login", fields(inspectorName, area),
                AuthResponse.class, callback);
    }

    public void logout(Callback<Void> callback) {
        request("POST", "/api/auth/logout", null, Void.class, callback);
    }

    public void me(Callback<User> callback) {
        request("GET", "/api/auth/me", null, User.class, callback);
    }

    public void listModels(Callback<List<VehicleModel>> callback) {
        request("GET", "/api/models", null,
                new TypeToken<List<VehicleModel>>() { }.getType(), callback);
    }

    public void createModel(String code, String modelName, Callback<VehicleModel> callback) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("modelName", modelName);
        request("POST", "/api/models", body, VehicleModel.class, callback);
    }

    public void updateModel(long id, String code, String modelName,
                            Callback<VehicleModel> callback) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("modelName", modelName);
        request("PUT", "/api/models/" + id, body, VehicleModel.class, callback);
    }

    public void deleteModel(long id, Callback<Void> callback) {
        request("DELETE", "/api/models/" + id, null, Void.class, callback);
    }

    public void listRecords(String date, Callback<List<InspectionRecord>> callback) {
        request("GET", "/api/records?date=" + Uri.encode(date), null,
                new TypeToken<List<InspectionRecord>>() { }.getType(), callback);
    }

    public void submitBatch(String date, int round, int line, List<DraftVehicle> vehicles,
                            Callback<List<InspectionRecord>> callback) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workDate", date);
        body.put("roundNumber", round);
        body.put("lineNumber", line);
        body.put("vehicles", vehicles);
        request("POST", "/api/records/batches", body,
                new TypeToken<List<InspectionRecord>>() { }.getType(), callback);
    }

    public void getExcelDates(Callback<List<String>> callback) {
        request("GET", "/api/excel/dates", null,
                new TypeToken<List<String>>() { }.getType(), callback);
    }

    public void sendFeishu(String date, Callback<Void> callback) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", date);
        request("POST", "/api/excel/feishu", body, Void.class, callback);
    }

    public void listUsers(Callback<List<User>> callback) {
        request("GET", "/api/admin/users", null,
                new TypeToken<List<User>>() { }.getType(), callback);
    }

    public void updateUser(User user, Callback<User> callback) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inspectorName", user.inspectorName);
        body.put("area", user.area);
        body.put("role", user.role);
        body.put("enabled", user.enabled);
        body.put("feishuOpenId", user.feishuOpenId);
        request("PATCH", "/api/admin/users/" + user.id, body, User.class, callback);
    }

    public void saveFeishu(String openId, Callback<Void> callback) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("feishuOpenId", openId);
        request("PUT", "/api/profile/feishu", body, Void.class, callback);
    }

    public void uploadTemplate(Uri uri, String fileName, Callback<Void> callback) {
        RequestBody fileBody = new RequestBody() {
            @Override
            public MediaType contentType() {
                String mime = context.getContentResolver().getType(uri);
                return MediaType.get(mime == null
                        ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : mime);
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                InputStream input = context.getContentResolver().openInputStream(uri);
                if (input == null) {
                    throw new FileNotFoundException("无法读取所选文件");
                }
                try (InputStream stream = input) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = stream.read(buffer)) != -1) {
                        sink.write(buffer, 0, read);
                    }
                }
            }
        };
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .build();
        Request.Builder builder = new Request.Builder().url(url("/api/admin/template")).post(body);
        String token = session.getToken();
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        enqueue(builder.build(), Void.class, callback);
    }

    public long downloadExcel(String date) {
        DownloadManager.Request request = new DownloadManager.Request(
                Uri.parse(url("/api/excel/" + Uri.encode(date) + ".xlsx")));
        String token = session.getToken();
        if (token != null && !token.isEmpty()) {
            request.addRequestHeader("Authorization", "Bearer " + token);
        }
        request.setTitle("质量检查记录 " + date)
                .setMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                        "quality-" + date + "-" + System.currentTimeMillis() + ".xlsx")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true);
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        return manager.enqueue(request);
    }
}

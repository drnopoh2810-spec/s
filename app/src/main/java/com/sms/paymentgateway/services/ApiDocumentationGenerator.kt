package com.sms.paymentgateway.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.os.Environment
import com.sms.paymentgateway.utils.security.SecurityManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class DocLanguage(val label: String, val ext: String) {
    CURL("cURL", "sh"),
    JAVASCRIPT("JavaScript", "js"),
    PYTHON("Python", "py"),
    PHP("PHP", "php"),
    KOTLIN("Kotlin", "kt"),
    JAVA("Java", "java"),
    DART("Dart / Flutter", "dart"),
    CSHARP("C#", "cs")
}

@Singleton
class ApiDocumentationGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager
) {

    /** يولّد نص الدوكيومنتيشن الكامل للغة المطلوبة */
    fun generate(lang: DocLanguage): String {
        val url = securityManager.buildDirectApiUrl() ?: "http://PHONE_IP:8080/api/v1"
        val key = securityManager.getApiKey()
        return when (lang) {
            DocLanguage.CURL       -> buildCurl(url, key)
            DocLanguage.JAVASCRIPT -> buildJs(url, key)
            DocLanguage.PYTHON     -> buildPython(url, key)
            DocLanguage.PHP        -> buildPhp(url, key)
            DocLanguage.KOTLIN     -> buildKotlin(url, key)
            DocLanguage.JAVA       -> buildJava(url, key)
            DocLanguage.DART       -> buildDart(url, key)
            DocLanguage.CSHARP     -> buildCsharp(url, key)
        }
    }

    /** يحفظ الملف في Downloads ويعيد مساره */
    fun saveToDownloads(lang: DocLanguage): File {
        val content  = generate(lang)
        val fileName = "sms_gateway_api.${lang.ext}"
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    // ─── cURL ────────────────────────────────────────────────────────────────
    private fun buildCurl(url: String, key: String) = """
# ============================================================
#  SMS Payment Gateway — API Documentation (cURL)
# ============================================================
#  Base URL : $url
#  API Key  : $key
# ============================================================

# 1) Health Check
curl -X GET "$url/health" \
  -H "Authorization: Bearer $key"

# 2) Create Transaction
curl -X POST "$url/transactions" \
  -H "Authorization: Bearer $key" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "order-001",
    "amount": 500.00,
    "phoneNumber": "01012345678",
    "walletType": "VODAFONE_CASH",
    "expiresInMinutes": 30
  }'

# 3) Get Transaction Status
curl -X GET "$url/transactions/order-001" \
  -H "Authorization: Bearer $key"

# 4) List All Transactions
curl -X GET "$url/transactions" \
  -H "Authorization: Bearer $key"

# 5) SMS Logs
curl -X GET "$url/sms/logs" \
  -H "Authorization: Bearer $key"

# 6) Connection Info
curl -X GET "$url/connection-info" \
  -H "Authorization: Bearer $key"

# ─── WebSocket (wscat) ───────────────────────────────────────
# npm install -g wscat
# wscat -c "ws://PHONE_IP:8080/websocket"
# Then send: {"type":"subscribe"}
""".trimIndent()

    // ─── JavaScript ──────────────────────────────────────────────────────────
    private fun buildJs(url: String, key: String): String {
        val d = "$" // dollar sign for JS template literals
        return """
// ============================================================
//  SMS Payment Gateway — API Documentation (JavaScript)
// ============================================================
const BASE_URL = "$url";
const API_KEY  = "$key";

const headers = {
  "Authorization": `Bearer ${d}{API_KEY}`,
  "Content-Type": "application/json"
};

// 1) Health Check
async function healthCheck() {
  const res = await fetch(`${d}{BASE_URL}/health`, { headers });
  return res.json();
}

// 2) Create Transaction
async function createTransaction(id, amount, phone, walletType = "VODAFONE_CASH") {
  const res = await fetch(`${d}{BASE_URL}/transactions`, {
    method: "POST", headers,
    body: JSON.stringify({ id, amount, phoneNumber: phone, walletType, expiresInMinutes: 30 })
  });
  return res.json();
}

// 3) Get Transaction Status
async function getTransaction(id) {
  const res = await fetch(`${d}{BASE_URL}/transactions/${d}{id}`, { headers });
  return res.json();
}

// 4) Poll Until Confirmed (max 5 min)
async function waitForPayment(id, intervalMs = 5000, maxMs = 300000) {
  const start = Date.now();
  while (Date.now() - start < maxMs) {
    const tx = await getTransaction(id);
    if (tx.status === "MATCHED") return tx;
    await new Promise(r => setTimeout(r, intervalMs));
  }
  throw new Error("Payment timeout");
}

// 5) WebSocket — Real-time Notifications
function connectWebSocket(onPayment) {
  const wsUrl = BASE_URL.replace("http://", "ws://").replace("https://", "wss://")
                        .replace("/api/v1", "") + "/websocket";
  const ws = new WebSocket(wsUrl);
  ws.onopen    = () => ws.send(JSON.stringify({ type: "subscribe" }));
  ws.onmessage = (e) => {
    const msg = JSON.parse(e.data);
    if (msg.event === "PAYMENT_CONFIRMED") onPayment(msg.data);
  };
  ws.onclose   = () => setTimeout(() => connectWebSocket(onPayment), 5000);
  return ws;
}

// ─── Usage Example ───────────────────────────────────────────
(async () => {
  await createTransaction("order-001", 500, "01012345678");
  connectWebSocket(data => console.log("Payment confirmed:", data));
})();
""".trimIndent()
    }

    // ─── Python ──────────────────────────────────────────────────────────────
    private fun buildPython(url: String, key: String): String {
        return """
# ============================================================
#  SMS Payment Gateway — API Documentation (Python)
#  pip install requests websocket-client
# ============================================================
import requests, json, time, threading
import websocket

BASE_URL = "$url"
API_KEY  = "$key"
HEADERS  = {"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"}

# 1) Health Check
def health_check():
    return requests.get(f"{BASE_URL}/health", headers=HEADERS).json()

# 2) Create Transaction
def create_transaction(order_id, amount, phone, wallet="VODAFONE_CASH"):
    data = {"id": order_id, "amount": amount, "phoneNumber": phone,
            "walletType": wallet, "expiresInMinutes": 30}
    return requests.post(f"{BASE_URL}/transactions", headers=HEADERS, json=data).json()

# 3) Get Transaction Status
def get_transaction(order_id):
    return requests.get(f"{BASE_URL}/transactions/{order_id}", headers=HEADERS).json()

# 4) Poll Until Confirmed
def wait_for_payment(order_id, timeout=300, interval=5):
    start = time.time()
    while time.time() - start < timeout:
        tx = get_transaction(order_id)
        if tx.get("status") == "MATCHED":
            return tx
        time.sleep(interval)
    raise TimeoutError("Payment not confirmed within timeout")

# 5) WebSocket — Real-time Notifications
def on_payment_confirmed(ws, message):
    msg = json.loads(message)
    if msg.get("event") == "PAYMENT_CONFIRMED":
        print("Payment confirmed:", msg["data"])

def connect_websocket():
    ws_url = BASE_URL.replace("http://","ws://").replace("https://","wss://") \
                     .replace("/api/v1","") + "/websocket"
    ws = websocket.WebSocketApp(ws_url, on_message=on_payment_confirmed)
    ws.on_open = lambda w: w.send(json.dumps({"type": "subscribe"}))
    threading.Thread(target=ws.run_forever, daemon=True).start()
    return ws

# ─── Usage ───────────────────────────────────────────────────
if __name__ == "__main__":
    print(health_check())
    result = create_transaction("order-001", 500.0, "01012345678")
    print("Created:", result)
    connect_websocket()
    confirmed = wait_for_payment("order-001")
    print("Confirmed:", confirmed)
""".trimIndent()
    }

    // ─── PHP ─────────────────────────────────────────────────────────────────
    private fun buildPhp(url: String, key: String): String {
        val d = "$" // dollar sign for PHP variables
        return """
<?php
// ============================================================
//  SMS Payment Gateway — API Documentation (PHP)
// ============================================================
define('BASE_URL', '$url');
define('API_KEY',  '$key');

function apiRequest(string ${d}method, string ${d}endpoint, array ${d}data = []): array {
    ${d}ch = curl_init(BASE_URL . ${d}endpoint);
    curl_setopt_array(${d}ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CUSTOMREQUEST  => ${d}method,
        CURLOPT_HTTPHEADER     => [
            'Authorization: Bearer ' . API_KEY,
            'Content-Type: application/json'
        ],
    ]);
    if (!empty(${d}data)) curl_setopt(${d}ch, CURLOPT_POSTFIELDS, json_encode(${d}data));
    ${d}response = curl_exec(${d}ch);
    curl_close(${d}ch);
    return json_decode(${d}response, true) ?? [];
}

// 1) Health Check
function healthCheck(): array { return apiRequest('GET', '/health'); }

// 2) Create Transaction
function createTransaction(string ${d}id, float ${d}amount, string ${d}phone): array {
    return apiRequest('POST', '/transactions', [
        'id' => ${d}id, 'amount' => ${d}amount,
        'phoneNumber' => ${d}phone, 'walletType' => 'VODAFONE_CASH',
        'expiresInMinutes' => 30
    ]);
}

// 3) Get Transaction Status
function getTransaction(string ${d}id): array { return apiRequest('GET', "/transactions/${d}id"); }

// 4) Poll Until Confirmed
function waitForPayment(string ${d}id, int ${d}timeout = 300): ?array {
    ${d}start = time();
    while (time() - ${d}start < ${d}timeout) {
        ${d}tx = getTransaction(${d}id);
        if ((${d}tx['status'] ?? '') === 'MATCHED') return ${d}tx;
        sleep(5);
    }
    return null;
}

// ─── Usage ───────────────────────────────────────────────────
${d}tx = createTransaction('order-001', 500.0, '01012345678');
echo "Created: " . json_encode(${d}tx) . PHP_EOL;

${d}confirmed = waitForPayment('order-001');
echo "Confirmed: " . json_encode(${d}confirmed) . PHP_EOL;
?>
""".trimIndent()
    }

    // ─── Kotlin ──────────────────────────────────────────────────────────────
    private fun buildKotlin(url: String, key: String): String {
        val d = "$" // dollar sign for Kotlin string templates
        val tq = "\"\"\"" // triple-quote
        return """
// ============================================================
//  SMS Payment Gateway — API Documentation (Kotlin)
//  implementation("com.squareup.okhttp3:okhttp:4.12.0")
// ============================================================
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object SmsGateway {
    private const val BASE_URL = "$url"
    private const val API_KEY  = "$key"
    private val JSON = "application/json".toMediaType()
    private val client = OkHttpClient()

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val reqBody = body?.toString()?.toRequestBody(JSON)
        val req = Request.Builder()
            .url("${d}{BASE_URL}${d}{path}")
            .header("Authorization", "Bearer ${d}{API_KEY}")
            .method(method, reqBody)
            .build()
        client.newCall(req).execute().use { return JSONObject(it.body!!.string()) }
    }

    fun healthCheck() = request("GET", "/health")

    fun createTransaction(id: String, amount: Double, phone: String) =
        request("POST", "/transactions", JSONObject().apply {
            put("id", id); put("amount", amount)
            put("phoneNumber", phone); put("walletType", "VODAFONE_CASH")
            put("expiresInMinutes", 30)
        })

    fun getTransaction(id: String) = request("GET", "/transactions/${d}{id}")

    fun waitForPayment(id: String, timeoutMs: Long = 300_000): JSONObject? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val tx = getTransaction(id)
            if (tx.optString("status") == "MATCHED") return tx
            Thread.sleep(5_000)
        }
        return null
    }

    // WebSocket — Real-time
    fun connectWebSocket(onPayment: (JSONObject) -> Unit): WebSocket {
        val wsUrl = BASE_URL.replace("http://","ws://").replace("https://","wss://")
                            .replace("/api/v1","") + "/websocket"
        val req = Request.Builder().url(wsUrl).build()
        return client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, r: Response) =
                ws.send("{\"type\":\"subscribe\"}")
            override fun onMessage(ws: WebSocket, text: String) {
                val msg = JSONObject(text)
                if (msg.optString("event") == "PAYMENT_CONFIRMED")
                    onPayment(msg.getJSONObject("data"))
            }
        })
    }
}

// ─── Usage ───────────────────────────────────────────────────
fun main() {
    SmsGateway.createTransaction("order-001", 500.0, "01012345678")
    SmsGateway.connectWebSocket { println("Confirmed: ${d}{it}") }
    val result = SmsGateway.waitForPayment("order-001")
    println("Result: ${d}{result}")
}
""".trimIndent()
    }

    // ─── Java ────────────────────────────────────────────────────────────────
    private fun buildJava(url: String, key: String): String {
        return """
// ============================================================
//  SMS Payment Gateway — API Documentation (Java)
//  <dependency>com.squareup.okhttp3:okhttp:4.12.0</dependency>
// ============================================================
import okhttp3.*;
import org.json.JSONObject;

public class SmsGateway {
    private static final String BASE_URL = "$url";
    private static final String API_KEY  = "$key";
    private static final MediaType JSON  = MediaType.get("application/json");
    private final OkHttpClient client   = new OkHttpClient();

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        RequestBody reqBody = body != null ? RequestBody.create(body.toString(), JSON) : null;
        Request req = new Request.Builder()
            .url(BASE_URL + path)
            .header("Authorization", "Bearer " + API_KEY)
            .method(method, reqBody)
            .build();
        try (Response res = client.newCall(req).execute()) {
            return new JSONObject(res.body().string());
        }
    }

    public JSONObject healthCheck() throws Exception {
        return request("GET", "/health", null);
    }

    public JSONObject createTransaction(String id, double amount, String phone) throws Exception {
        JSONObject body = new JSONObject();
        body.put("id", id); body.put("amount", amount);
        body.put("phoneNumber", phone); body.put("walletType", "VODAFONE_CASH");
        body.put("expiresInMinutes", 30);
        return request("POST", "/transactions", body);
    }

    public JSONObject getTransaction(String id) throws Exception {
        return request("GET", "/transactions/" + id, null);
    }

    public JSONObject waitForPayment(String id, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            JSONObject tx = getTransaction(id);
            if ("MATCHED".equals(tx.optString("status"))) return tx;
            Thread.sleep(5000);
        }
        return null;
    }

    // ─── Usage ───────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        SmsGateway gw = new SmsGateway();
        gw.createTransaction("order-001", 500.0, "01012345678");
        JSONObject result = gw.waitForPayment("order-001", 300_000);
        System.out.println("Result: " + result);
    }
}
""".trimIndent()
    }

    // ─── Dart / Flutter ──────────────────────────────────────────────────────
    private fun buildDart(url: String, key: String): String {
        val d = "$" // dollar sign for Dart string interpolation
        return """
// ============================================================
//  SMS Payment Gateway — API Documentation (Dart / Flutter)
//  dependencies: http: ^1.2.0  web_socket_channel: ^2.4.0
// ============================================================
import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:web_socket_channel/web_socket_channel.dart';

class SmsGateway {
  static const baseUrl = '$url';
  static const apiKey  = '$key';
  static final _headers = {
    'Authorization': 'Bearer ${d}{apiKey}',
    'Content-Type': 'application/json',
  };

  // 1) Health Check
  static Future<Map> healthCheck() async {
    final res = await http.get(Uri.parse('${d}{baseUrl}/health'), headers: _headers);
    return jsonDecode(res.body);
  }

  // 2) Create Transaction
  static Future<Map> createTransaction(String id, double amount, String phone) async {
    final res = await http.post(
      Uri.parse('${d}{baseUrl}/transactions'),
      headers: _headers,
      body: jsonEncode({'id': id, 'amount': amount, 'phoneNumber': phone,
                        'walletType': 'VODAFONE_CASH', 'expiresInMinutes': 30}),
    );
    return jsonDecode(res.body);
  }

  // 3) Get Transaction
  static Future<Map> getTransaction(String id) async {
    final res = await http.get(Uri.parse('${d}{baseUrl}/transactions/${d}{id}'), headers: _headers);
    return jsonDecode(res.body);
  }

  // 4) Poll Until Confirmed
  static Future<Map?> waitForPayment(String id, {Duration timeout = const Duration(minutes: 5)}) async {
    final end = DateTime.now().add(timeout);
    while (DateTime.now().isBefore(end)) {
      final tx = await getTransaction(id);
      if (tx['status'] == 'MATCHED') return tx;
      await Future.delayed(const Duration(seconds: 5));
    }
    return null;
  }

  // 5) WebSocket — Real-time
  static WebSocketChannel connectWebSocket() {
    final wsUrl = baseUrl.replaceFirst('http://', 'ws://')
                         .replaceFirst('https://', 'wss://')
                         .replaceFirst('/api/v1', '') + '/websocket';
    final channel = WebSocketChannel.connect(Uri.parse(wsUrl));
    channel.sink.add(jsonEncode({'type': 'subscribe'}));
    return channel;
  }
}

// ─── Usage (Flutter Widget) ──────────────────────────────────
void main() async {
  await SmsGateway.createTransaction('order-001', 500.0, '01012345678');
  final channel = SmsGateway.connectWebSocket();
  channel.stream.listen((msg) {
    final data = jsonDecode(msg);
    if (data['event'] == 'PAYMENT_CONFIRMED') print('Confirmed: ${d}{data['data']}');
  });
  final result = await SmsGateway.waitForPayment('order-001');
  print('Result: ${d}{result}');
}
""".trimIndent()
    }

    // ─── C# ──────────────────────────────────────────────────────────────────
    private fun buildCsharp(url: String, key: String): String {
        val d = "$" // dollar sign for C# string interpolation
        return """
// ============================================================
//  SMS Payment Gateway — API Documentation (C#)
//  NuGet: Install-Package Newtonsoft.Json
//  NuGet: Install-Package System.Net.Http
// ============================================================
using System;
using System.Net.Http;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

public class SmsGateway
{
    private const string BaseUrl = "$url";
    private const string ApiKey  = "$key";
    private static readonly HttpClient client = new HttpClient();

    static SmsGateway()
    {
        client.DefaultRequestHeaders.Add("Authorization", ${d}"Bearer {ApiKey}");
    }

    // 1) Health Check
    public static async Task<JObject> HealthCheck()
    {
        var response = await client.GetAsync(${d}"{BaseUrl}/health");
        var json = await response.Content.ReadAsStringAsync();
        return JObject.Parse(json);
    }

    // 2) Create Transaction
    public static async Task<JObject> CreateTransaction(string id, double amount, string phone)
    {
        var data = new {
            id, amount, phoneNumber = phone,
            walletType = "VODAFONE_CASH", expiresInMinutes = 30
        };
        var content = new StringContent(JsonConvert.SerializeObject(data), Encoding.UTF8, "application/json");
        var response = await client.PostAsync(${d}"{BaseUrl}/transactions", content);
        var json = await response.Content.ReadAsStringAsync();
        return JObject.Parse(json);
    }

    // 3) Get Transaction Status
    public static async Task<JObject> GetTransaction(string id)
    {
        var response = await client.GetAsync(${d}"{BaseUrl}/transactions/{id}");
        var json = await response.Content.ReadAsStringAsync();
        return JObject.Parse(json);
    }

    // 4) Poll Until Confirmed
    public static async Task<JObject> WaitForPayment(string id, int timeoutMs = 300000)
    {
        var end = DateTime.Now.AddMilliseconds(timeoutMs);
        while (DateTime.Now < end)
        {
            var tx = await GetTransaction(id);
            if (tx["status"]?.ToString() == "MATCHED") return tx;
            await Task.Delay(5000);
        }
        return null;
    }

    // 5) WebSocket — Real-time Notifications
    public static async Task ConnectWebSocket(Action<JObject> onPayment)
    {
        var wsUrl = BaseUrl.Replace("http://", "ws://").Replace("https://", "wss://")
                           .Replace("/api/v1", "") + "/websocket";
        using var ws = new ClientWebSocket();
        await ws.ConnectAsync(new Uri(wsUrl), CancellationToken.None);
        
        var subscribeMsg = Encoding.UTF8.GetBytes(JsonConvert.SerializeObject(new { type = "subscribe" }));
        await ws.SendAsync(new ArraySegment<byte>(subscribeMsg), WebSocketMessageType.Text, true, CancellationToken.None);

        var buffer = new byte[4096];
        while (ws.State == WebSocketState.Open)
        {
            var result = await ws.ReceiveAsync(new ArraySegment<byte>(buffer), CancellationToken.None);
            var message = Encoding.UTF8.GetString(buffer, 0, result.Count);
            var msg = JObject.Parse(message);
            if (msg["event"]?.ToString() == "PAYMENT_CONFIRMED")
                onPayment(msg["data"] as JObject);
        }
    }
}

// ─── Usage ───────────────────────────────────────────────────
class Program
{
    static async Task Main(string[] args)
    {
        await SmsGateway.CreateTransaction("order-001", 500.0, "01012345678");
        _ = SmsGateway.ConnectWebSocket(data => Console.WriteLine(${d}"Confirmed: {data}"));
        var result = await SmsGateway.WaitForPayment("order-001");
        Console.WriteLine(${d}"Result: {result}");
    }
}
""".trimIndent()
    }
}

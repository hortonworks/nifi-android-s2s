# Apache NiFi Site-to-Site Android library
*A small (currently ~= 150k), easy-to-use Android library for sending data to NiFi via the Site-to-Site protocol with only Android SDK dependencies.*

## Building
```shell
export ANDROID_HOME=YOUR_SDK_DIR
./gradlew clean build
```

## Testing
To run the Android Test classes (on-device tests) along with the build start up an an Android Virtual Device and then run the following:
```shell
export ANDROID_HOME=YOUR_SDK_DIR
./gradlew clean connectedAndroidTest build
```

## Structure
* s2s: Android library
* demo: Demo app

## Usage
### Setup
SiteToSite can be configured via Java code:
```java
// Need to be on right thread if updating UI, can return null handler in callback otherwise
final Handler handler = new Handler(Looper.getMainLooper());

SiteToSiteRemoteCluster siteToSiteRemoteCluster = new SiteToSiteRemoteCluster();
siteToSiteRemoteCluster.setUrls(Collections.singletonList("http://nifi.hostname:8080/nifi"));

SiteToSiteClientConfig siteToSiteClientConfig = new SiteToSiteClientConfig();
siteToSiteClientConfig.setRemoteClusters(Collections.singletonList(siteToSiteRemoteCluster));
siteToSiteClientConfig.setPortName("From Android");
```

#### Failover
Failover can be configured by setting multiple remote clusters on the SiteToSiteClientConfig object.  The client will then try the remote clusters in the order they were specified.  This should result in failing over when necessary and then going back to the primary cluster when it is available again.
```java
SiteToSiteRemoteCluster siteToSiteRemoteCluster = new SiteToSiteRemoteCluster();
siteToSiteRemoteCluster.setUrls(Arrays.asList("http://nifi.primary.hostname:8080/nifi"));

SiteToSiteRemoteCluster siteToSiteFailoverRemoteCluster = new SiteToSiteRemoteCluster();
siteToSiteFailoverRemoteCluster.setUrls(Arrays.asList("http://nifi.failover.hostname:8080/nifi"));

SiteToSiteClientConfig siteToSiteClientConfig = new SiteToSiteClientConfig();
siteToSiteClientConfig.setRemoteClusters(Arrays.asList(siteToSiteRemoteCluster, siteToSiteFailoverRemoteCluster));
siteToSiteClientConfig.setPortName("From Android");
```

#### Properties file configuration
SiteToSite configuration can also be [initialized from a properties file](demo/src/main/java/com/hortonworks/hdf/android/sitetositedemo/MainActivity.java#L87).  There are several sample configurations in [the demo app's resources folder](demo/src/main/resources).  The configuration properties that can be set are as follows.

| Property | Description |
| --- | --- |
| s2s.config.remote.cluster.{X}.url.{Y} | The url of host Y in cluster X, where X and Y are zero-based indices indicating order/priority of clusters and hosts withing clusters. The primary host should be specified using `s2s.config.remote.cluster.0.url.0`. |
| s2s.config.remote.cluster.{X}.keystore | The path of the keystore holding the client's identity, either as a "classpath:/..." path or a file path. This can be specified for each cluster by setting {X} to the index of the cluster. |
| s2s.config.remote.cluster.{X}.keystorePasswd | The password to use to open the keystore. This can be specified for each cluster by setting {X} to the index of the cluster. |
| s2s.config.remote.cluster.{X}.keystoreType | The type of keystore. Currently only `BKS` is supported and should be used. |
| s2s.config.remote.cluster.{X}.truststore | The path of the truststore holding the server's CA trust chain, either as a "classpath:/..." path or a file path. This can be specified for each cluster by setting {X} to the index of the cluster. |
| s2s.config.remote.cluster.{X}.truststorePasswd | The password to use to open the truststore. This can be specified for each cluster by setting {X} to the index of the cluster. |
| s2s.config.remote.cluster.{X}.truststoreType | The type of truststore. Currently only `BKS` is supported and should be used. |
| s2s.config.remote.cluster.{X}.proxyHost | If the cluster is accessed through an HTTP Proxy, this can be used to specify the host of the proxy. |
| s2s.config.remote.cluster.{X}.proxyPort | If the cluster is accessed through an HTTP Proxy, this can be used to specify the port of the proxy. |
| s2s.config.remote.cluster.{X}.proxyAuthorizationType | If the cluster is accessed through an HTTP Proxy and the HTTP Proxy requires an Authorization HTTP Header, this is the type of Authorization to include in the Header, e.g., "Basic"  |
| s2s.config.remote.cluster.{X}.proxyUsername | If the cluster is accessed through an HTTP Proxy and the HTTP Proxy requires an Authorization HTTP Header, this is the username to to use to authenticate. |
| s2s.config.remote.cluster.{X}.proxyPassword | If the cluster is accessed through an HTTP Proxy and the HTTP Proxy requires an Authorization HTTP Header, this is the password to to use to authenticate. |
| s2s.config.remote.cluster.{X}.clientType | The transport protocol the client should use to communicate to this cluster. Currently supports `HTTP(S)` or `RAW`. Defaults to `HTTP(S)`. |
| s2s.config.timeout | The client-side timeout in **milliseconds** when communicating with a remote NiFi instance/cluster over the SiteToSite protocol and waiting for a response. Defaults to 30,000 milliseconds (i.e., 30 seconds). |
| s2s.config.idleConnectionExpiration | The time in **milliseconds** after which idle connections will be closed. An idle connection is one for which no data has passed either direction. Defaults to 30,000 milliseconds (i.e., 30 seconds). |
| s2s.config.useCompression | A boolean (`true`\|`false`) indicating if compression should be used when transmitting data over the SiteToSite protocol. Defaults to `false` if not specified. |
| s2s.config.portName | The name of the input port in the flow running on the remote NiFi instance/cluster to which this client should send SiteToSite data. This is discoverable in the NiFi flow configuration or Web UI. That port must be running (it cannot be stopped) in order for the client to connect and send data. |
| s2s.config.portIdentifier | The id (UUID) of the input port in the flow running on the remote NiFi instance/cluster to which this client should send SiteToSite data. This is discoverable in the NiFi flow configuration or Web UI. This property is an alternative to `s2s.config.portName`; only one should be set. |
| s2s.config.preferredBatchCount | When batching flow file data packets for transmission, this is the preferred number of flow file data packets to send in each batch.  It is treated as a guideline by the library for the desired batch count, and each batch will contain <= this number of flow files if specified. Defaults to 100 if not specified. | 
| s2s.config.peerUpdateInterval | How often, in **milliseconds**, this client should refresh its peer list by communicating with the remote NiFi cluster. The peer list includes the hosts in the NiFi cluster and how many flow files they have received, information used by the client for load balancing. Defaults to 30 minutes (i.e., 1.8E+6 milliseconds). | 


Notes:
* When specifying clusters using `s2s.config.remote.cluster.{X}.url.{Y}`, multiple hosts in each cluster can be specified, and multiple clusters (i.e., failover clusters) can be specified. Each one gets its own property field with the host and cluster indices specified. Note that these URLs are only used for initializing the client and once it connects to a cluster it will discover and use all active hosts in that cluster. Therefore, only one host in each cluster is necessary, although multiple can be specified, which may be desirable in the case that the primary host is not reachable. If an entire cluster is not reachable, the client will switch to the next specified cluster until one is reachable. For an example, see the provided [failover.properties](demo/src/main/resources/failover.properties) in the resources folder of the included Demo app.

### Sample data
Example data packet(s) that will be used in below examples
```java
Map<String, String> attributes = new HashMap<>();
attributes.put("key", "value");
DataPacket dataPacket = new ByteArrayDataPacket(attributes, "message".getBytes(Charsets.UTF_8));

List<DataPacket> dataPackets = Arrays.asList(dataPacket)
```
### One-shot

```java
// Synchronous
SiteToSiteClient siteToSiteClient = siteToSiteClientConfig.createClient();
Transaction transaction = siteToSiteClient.createTransaction();

transaction.send(dataPacket);
// or
for (DataPacket dataPacket : dataPackets) {
  transaction.send(dataPacket);
}

transaction.confirm();
TransactionResult transactionResult = transaction.complete();

// Asynchronous
SiteToSiteService.sendDataPacket(context, dataPacket, siteToSiteClientConfig, new TransactionResultCallback() {});
SiteToSiteService.sendDataPackets(context, dataPackets, siteToSiteClientConfig, new TransactionResultCallback() {});
```

### Repeating
This example schedules a repeating callback using the AlarmManager to dataCollector.getDataPackets() and sends the results to NiFi.  This repeating alarm will persist even when the app is terminated.

```java
// Can be any ParcelableTransactionResultCallback implementation
ParcelableTransactionResultCallback parcelableTransactionResultCallback = new RepeatingTransactionResultCallback();

// Can be any DataCollector implementation
DataCollector dataCollector = new TestDataCollector("message");
SiteToSiteRepeatableIntent siteToSiteRepeatableIntent = SiteToSiteRepeating.createSendPendingIntent(context, dataCollector, siteToSiteClientConfig, parcelableTransactionResultCallback);

AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), TimeUnit.MINUTES.toMillis(15), siteToSiteRepeatableIntent.getPendingIntent());
```

### Queued Processing
In many cases, it can be useful to queue up data to send to NiFi and then wait until more favorable conditions to send the data.

This can be easily accomplished by Queuing data using the SiteToSiteService and/or the SiteToSiteRepeatingService and then using the JobScheduler to send the data when the desired criteria is met.
#### Setup
```java
SiteToSiteRemoteCluster siteToSiteRemoteCluster = new SiteToSiteRemoteCluster();
siteToSiteRemoteCluster.setUrls(Arrays.asList("http://nifi.hostname:8080/nifi"));

QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig = new QueuedSiteToSiteClientConfig();
queuedSiteToSiteClientConfig.setRemoteClusters(Collections.singletonList(siteToSiteRemoteCluster));
queuedSiteToSiteClientConfig.setPortName("From Android");

// Set the maximum amount of time before a transaction is considered failed in case it failed but was unable to update database to indicate that.  This should be set much higher than transactions will be reasonably expected to take and defaults to 10 minutes.
queuedSiteToSiteClientConfig.setMaxTransactionTime(20, TimeUnit.MINUTES);

// Optionally set a data packet prioritizer.  This is responsible for assigning a numeric priority to each packet (higher is more important) as well as a TTL value beyond which the packet should be discarded whether sent or not.
queuedSiteToSiteClientConfig.setDataPacketPrioritizer(new YourCustomDataPacketPrioritizer());

// Optionally set the maximum number of rows to retain when cleanup is performed
queuedSiteToSiteClientConfig.setMaxRows(10000);

// Optionally set the maximum size to use storing the attributes and flowfile content in the database.
queuedSiteToSiteClientConfig.setMaxSize(1024 * 100);
```

These queuing configuration options can also be set in the properties file along with the other SiteToSite settings:

| Property | Description |
| --- | --- |
| s2s.config.maxRows | When using the SiteToSiteService interface that queues flow file data packets in a local database, this controls the maximum number of data packets to keep in the local buffer prior before starting to age off flow files. Defaults to 10,000 | 
| s2s.config.maxSize | When using the SiteToSiteService interface that queues flow file data packets in a local database, this controls the maximum number of **bytes** to keep in the local buffer prior before starting to age off flow files. Defaults to 10 MB. Note, it is a more expensive operation to age off by size than by row count or TTL using the Data Packet Prioritizer. | 
| s2s.config.maxTransactionTime | When using the SiteToSiteService interface that queues flow file data packets in a local database, this controls the maximum duration, in **milliseconds** of an attempted batch / transaction before it will be marked as failed and the data file flow packets will be returned to the local queue where they can be picked up in a future transaction attempt for retry. Defaults to 10 minutes. | 

#### Enqueue
Add data packet(s) to queue to send later
```java
// Synchronous
QueuedSiteToSiteClient queuedSiteToSiteClient = queuedSiteToSiteClientConfig.createQueuedClient(context);
queuedSiteToSiteClient.enqueue(dataPacket);
queuedSiteToSiteClient.enqueue(dataPackets.iterator());

// Asynchronous
SiteToSiteService.enqueueDataPacket(context, dataPacket, queuedSiteToSiteClientConfig, new QueuedOperationResultCallback(){});
SiteToSiteService.enqueueDataPackets(context, dataPackets, queuedSiteToSiteClientConfig, new QueuedOperationResultCallback(){});

// Repeating
SiteToSiteRepeatableIntent siteToSiteRepeatableIntent = SiteToSiteRepeating.createEnqueuePendingIntent(context, dataCollector, queuedSiteToSiteClientConfig, parcelableQueuedOperationResultCallback);
AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), TimeUnit.MINUTES.toMillis(15), siteToSiteRepeatableIntent.getPendingIntent());
```

#### Process
Try to send all queued data packets whose TTL hasn't expired to NiFi
```java
// Synchronous
queuedSiteToSiteClient.process();

// Asynchronous
SiteToSiteService.processQueuedPackets(context, queuedSiteToSiteClientConfig, new QueuedOperationResultCallback(){});

// Repeating
SiteToSiteRepeatableIntent siteToSiteRepeatableIntent = SiteToSiteRepeating.createProcessQueuePendingIntent(context, queuedSiteToSiteClientConfig, parcelableQueuedOperationResultCallback);
AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), TimeUnit.MINUTES.toMillis(15), siteToSiteRepeatableIntent.getPendingIntent());

// Criteria-based
JobInfo.Builder processJobInfoBuilder = SiteToSiteJobService.createProcessJobInfoBuilder(context, jobId, queuedSiteToSiteClientConfig, parcelableQueuedOperationResultCallback);
processJobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
processJobInfoBuilder.setRequiresCharging(true);
JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
jobScheduler.schedule(processJobInfoBuilder.build());
```

#### Cleanup
Remove rows to satisfy maxRows, maxSize, ttl criteria
```java
// Synchronous
queuedSiteToSiteClient.cleanup();

// Asynchronous
SiteToSiteService.cleanupQueuedPackets(context, queuedSiteToSiteClientConfig, new QueuedOperationResultCallback(){});

// Repeating
SiteToSiteRepeatableIntent siteToSiteRepeatableIntent = SiteToSiteRepeating.createCleanupQueuePendingIntent(context, queuedSiteToSiteClientConfig, parcelableQueuedOperationResultCallback);
AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), TimeUnit.MINUTES.toMillis(15), siteToSiteRepeatableIntent.getPendingIntent());
```

## FAQ
Q. My keystore/truststore fails to load

A. Android doesn't support the JKS keystore type.  Recommend converting to BKS:
```shell
keytool -importkeystore -srckeystore truststore.jks -srcstoretype JKS -srcstorepass YOUR_JKS_PASSWORD -destkeystore truststore.bks -deststoretype BKS -deststorepass DESIRED_BKS_PASSWORD -provider org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath YOUR_NIFI_HOME_DIR/lib/bootstrap/bcprov-jdk15on-1.55.jar
```

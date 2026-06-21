# Proguard rules for RAMChat
# Keep our data models from being obfuscated/shrunk to avoid GSON deserialization issues
-keep class com.ramchat.model.** { *; }

# Keep Eclipse Paho MQTT library classes
-keep class org.eclipse.paho.client.mqttv3.** { *; }

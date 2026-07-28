plugins {
    java
    application
}

group = "pcd.fsstat"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("pcd.fsstat.reactive.TestRx")
}

dependencies {
    implementation("io.vertx:vertx-core:5.0.11")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
}

repositories {
    mavenCentral()
}

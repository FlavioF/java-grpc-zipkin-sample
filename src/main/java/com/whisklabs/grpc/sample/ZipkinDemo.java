package com.whisklabs.grpc.sample;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.EmptySpanCollectorMetricsHandler;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.grpc.BraveGrpcClientInterceptor;
import com.github.kristofa.brave.http.HttpSpanCollector;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import services.KeyValueServiceGrpc;
import services.KeyValueServiceGrpc.KeyValueServiceBlockingStub;
import services.UserRequest;
import services.UserServiceGrpc;
import services.UserServiceGrpc.UserServiceBlockingStub;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.ReporterMetrics;
import zipkin.reporter.urlconnection.URLConnectionSender;

import java.util.Iterator;

public class ZipkinDemo {

    private static String DOCKER_IP;

    public static void main(String args[]) throws Exception {
        DOCKER_IP = System.getProperty("docker.ip");
        if (Strings.isNullOrEmpty(DOCKER_IP)) {
            DOCKER_IP="localhost";
        }

        ManagedChannel kvChannel = ManagedChannelBuilder.forAddress("localhost", 15001)
            .intercept(BraveGrpcClientInterceptor.create(brave("kvClient")))
            .usePlaintext(true)
            .build();

        KeyValueServiceBlockingStub kvStub = KeyValueServiceGrpc.newBlockingStub(kvChannel);

        GrpcServer kvService = new GrpcServer(new KeyValueService(), 15001, brave("kv"));
        GrpcServer userService = new GrpcServer(new UserService(kvStub), 15002, brave("user"));

        kvService.start();
        userService.start();

        ManagedChannel userChannel = ManagedChannelBuilder.forAddress("localhost", 15002)
            .intercept(BraveGrpcClientInterceptor.create(brave("main")))
            .usePlaintext(true)
            .build();
        UserServiceBlockingStub userStub = UserServiceGrpc.newBlockingStub(userChannel);

        int i = 0;
        System.out.println("Making 100 RPC calls");
        Iterator<String> users = Iterators.cycle("karen", "bob", "john");
        while (users.hasNext() && i < 100) {
            userStub.getUser(UserRequest.newBuilder().setName(users.next()).build());
            i++;
        }
        System.out.println("RPC calls complete");

        kvChannel.shutdown();
        userChannel.shutdown();

        kvService.stop();
        userService.stop();
    }

    private static Brave brave(String serviceName) {
        return new Brave.Builder(serviceName)
            .traceSampler(Sampler.ALWAYS_SAMPLE)
            .reporter(
                    AsyncReporter
                            .builder(URLConnectionSender.create(String.format("http://%s:9411/api/v1/spans", DOCKER_IP)))
                            .metrics(ReporterMetrics.NOOP_METRICS)
                            .build())
            .build();
    }

}

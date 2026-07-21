package com.cryptocarver.crypto.pqc;

import com.cryptocarver.crypto.PostQuantumOperations;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.function.Consumer;
import javafx.concurrent.Task;

public class PQCBenchmark extends Task<String> {

    private final String algorithm;
    private final int iterations;
    private volatile int completedIterations = 0;
    private final long[] keyGenTimes;
    private final long[] op1Times;
    private final long[] op2Times;
    private int pkSize = 0;
    private int skSize = 0;
    private int artifactSize = 0;

    public PQCBenchmark(String algorithm, int iterations) {
        this.algorithm = algorithm;
        this.iterations = iterations;
        this.keyGenTimes = new long[iterations];
        this.op1Times = new long[iterations];
        this.op2Times = new long[iterations];
    }

    public synchronized String getPartialResult() {
        int currentCount = completedIterations;
        if (currentCount == 0) {
            return "Benchmark canceled before completing any iterations.";
        }

        long totalKeyGenTime = 0;
        long totalOp1Time = 0;
        long totalOp2Time = 0;

        long[] validKeyGenTimes = Arrays.copyOf(keyGenTimes, currentCount);
        long[] validOp1Times = Arrays.copyOf(op1Times, currentCount);
        long[] validOp2Times = Arrays.copyOf(op2Times, currentCount);

        for (int i = 0; i < currentCount; i++) {
            totalKeyGenTime += validKeyGenTimes[i];
            totalOp1Time += validOp1Times[i];
            totalOp2Time += validOp2Times[i];
        }

        Arrays.sort(validKeyGenTimes);
        Arrays.sort(validOp1Times);
        Arrays.sort(validOp2Times);

        int p95Index = (int) Math.floor(currentCount * 0.95);
        if (p95Index >= currentCount) p95Index = currentCount - 1;

        StringBuilder sb = new StringBuilder();
        sb.append("=== PQC Benchmark: ").append(algorithm).append(" ===\n");
        sb.append("Iterations: ").append(iterations).append("\n\n");

        sb.append(String.format("Key Generation:\n - Mean: %.2f ms\n - Median: %.2f ms\n - p95: %.2f ms\n - Size (PK/SK): %d / %d bytes\n\n",
                (totalKeyGenTime / (double) currentCount) / 1000000.0,
                validKeyGenTimes[currentCount / 2] / 1000000.0,
                validKeyGenTimes[p95Index] / 1000000.0,
                pkSize, skSize));

        boolean isKem = PostQuantumOperations.ML_KEM_ALGORITHMS.contains(algorithm);
        if (isKem) {
            sb.append(String.format("Encapsulation:\n - Mean: %.2f ms\n - Median: %.2f ms\n - p95: %.2f ms\n - Ciphertext Size: %d bytes\n\n",
                    (totalOp1Time / (double) currentCount) / 1000000.0,
                    validOp1Times[currentCount / 2] / 1000000.0,
                    validOp1Times[p95Index] / 1000000.0,
                    artifactSize));
            sb.append(String.format("Decapsulation:\n - Mean: %.2f ms\n - Median: %.2f ms\n - p95: %.2f ms\n",
                    (totalOp2Time / (double) currentCount) / 1000000.0,
                    validOp2Times[currentCount / 2] / 1000000.0,
                    validOp2Times[p95Index] / 1000000.0));
        } else {
            sb.append(String.format("Sign:\n - Mean: %.2f ms\n - Median: %.2f ms\n - p95: %.2f ms\n - Signature Size: %d bytes\n\n",
                    (totalOp1Time / (double) currentCount) / 1000000.0,
                    validOp1Times[currentCount / 2] / 1000000.0,
                    validOp1Times[p95Index] / 1000000.0,
                    artifactSize));
            sb.append(String.format("Verify:\n - Mean: %.2f ms\n - Median: %.2f ms\n - p95: %.2f ms\n",
                    (totalOp2Time / (double) currentCount) / 1000000.0,
                    validOp2Times[currentCount / 2] / 1000000.0,
                    validOp2Times[p95Index] / 1000000.0));
        }

        if (currentCount < iterations) {
            sb.append("\n[Benchmark canceled after ").append(currentCount).append(" iterations.]\n");
        }

        return sb.toString();
    }

    @Override
    protected String call() throws Exception {
        updateProgress(0, iterations);
        boolean isKem = PostQuantumOperations.ML_KEM_ALGORITHMS.contains(algorithm);
        byte[] dataToSign = "Benchmark Data".getBytes();

        for (int i = 0; i < iterations; i++) {
            if (isCancelled()) break;

            // KeyGen
            long start = System.nanoTime();
            KeyPair kp = PostQuantumOperations.generateKeyPair(algorithm);
            long end = System.nanoTime();
            long t = end - start;
            keyGenTimes[i] = t;

            if (i == 0) {
                pkSize = kp.getPublic().getEncoded().length;
                skSize = kp.getPrivate().getEncoded().length;
            }

            if (isKem) {
                start = System.nanoTime();
                PostQuantumOperations.KEMResult encap = PostQuantumOperations.encapsulate(kp.getPublic(), algorithm);
                end = System.nanoTime();
                op1Times[i] = (end - start);

                if (i == 0) artifactSize = encap.encapsulation().length;

                start = System.nanoTime();
                PostQuantumOperations.decapsulate(kp.getPrivate(), encap.encapsulation(), algorithm);
                end = System.nanoTime();
                op2Times[i] = (end - start);
            } else {
                start = System.nanoTime();
                byte[] sig = PostQuantumOperations.sign(kp.getPrivate(), dataToSign, algorithm);
                end = System.nanoTime();
                op1Times[i] = (end - start);

                if (i == 0) artifactSize = sig.length;

                start = System.nanoTime();
                PostQuantumOperations.verify(kp.getPublic(), dataToSign, sig, algorithm);
                end = System.nanoTime();
                op2Times[i] = (end - start);
            }

            completedIterations = i + 1;
            updateProgress(i + 1, iterations);
        }

        return getPartialResult();
    }
}

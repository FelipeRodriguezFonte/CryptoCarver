package com.cryptocarver.model.process;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Portable, secret-free description of a process designed on the workflow canvas. */
public final class ProcessDefinition {
    public int version = 3;
    public String name = "Untitled process";
    public List<Node> nodes = new ArrayList<>();
    public List<Connection> connections = new ArrayList<>();

    public static final class Node {
        public String id;
        public String type;
        public String label;
        public double x;
        public double y;
        public Map<String, String> configuration = new LinkedHashMap<>();

        public Node() { }
        public Node(String id, String type, String label, double x, double y) {
            this.id = id; this.type = type; this.label = label; this.x = x; this.y = y;
        }
    }

    public static final class Connection {
        public String from;
        public String to;
        public String targetPort; // Optional for backward compatibility, required for multi-input nodes
        public Connection() { }
        public Connection(String from, String to) { this.from = from; this.to = to; }
        public Connection(String from, String to, String targetPort) { this.from = from; this.to = to; this.targetPort = targetPort; }
    }
}

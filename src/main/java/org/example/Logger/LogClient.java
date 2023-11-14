package org.example.Logger;

import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public interface LogClient {
    void start(String processId, long timestamp); // listener for start

    void end(String processId); //listener for end

    String poll(); // Looks for completed process and sort them  by there start time and prints them
}


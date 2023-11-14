package org.example.Logger;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

public interface LogClient {
    void start(String processId); // listener for start

    void end(String processId, long timeStamp); //listener for end

    void poll(); // Looks for completed process and sort them  by there start time and prints them
}

class LoggerImplementation implements LogClient {

        private final Map<String, Process> processes;
        private final TreeMap<Long, Process> queue;


    public LoggerImplementation() {
        this.processes = new HashMap<>();
        this.queue = new TreeMap<>(new Comparator<Long>() {
            @Override
            public int compare(Long s1, Long s2) {
                return (int)(s1-s2);
            }
        });
    }

    @Override
    public void start(String processId) {
        final long now = System.currentTimeMillis();
        final Process process = new Process(processId, now);
        processes.put(processId,process);
        queue.put(now,process);


    }

    @Override
    public void end(String processId, long timeStamp) {
        final long now = System.currentTimeMillis();
        processes.get(processId).setEndTime(System.currentTimeMillis());
        // no need to update queue because map and queue have reference to same object


    }

    @Override
    public void poll() {
        final Process process   = queue.firstEntry().getValue();
        if(queue.isEmpty()) {
            System.out.println("Queue is empty");
            return;
        }

        if(process.getStartTime()!=-1)
        {
            System.out.println(process.getId()+"Started at "+ process.getStartTime()+"and ended at "+
                    process.getEndTime());
            processes.remove(process.getId());
            queue.pollFirstEntry();
        }
        else
        {
            System.out.println("No task in the queue");
        }

    }
}

@Getter
@Setter
class Process {
    private final long startTime;
    private final String id;
    private long endTime;

    public Process(final String id, final long startTime){
        this.id = id ;
        this.startTime=startTime;
        endTime=-1;
    }


}

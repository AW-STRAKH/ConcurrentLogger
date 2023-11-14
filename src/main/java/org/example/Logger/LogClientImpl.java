package org.example.Logger;

//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.*;
//import java.util.concurrent.locks.Lock;
//import java.util.concurrent.locks.ReentrantLock;
//
//public class LogClientImpl implements LogClient {
//
//    private final Map<String, Process> processes;
//    private final ConcurrentSkipListMap<Long, Process> queue; // priority queue is also fine // treemap for non-concurrent
//    private final List<CompletableFuture<Void>> futures; // future to add a process , will be kicked out when end is called for it
//    private final Lock lock;// need a lock on the object which is in concern since it is in both tree and map
//
//    private final ExecutorService[] taskScheduler;
//    // one guarantee is the start will come first to scheduler than end  as per client
//    // same thread to run end which runs start
//    // we are forcing end to come after start for a particular process and handled by a single thread.
//    //  after locks scope for concurrency is in start now which is mitigated by this
//
//    public LogClientImpl( int threads) {
//        this.processes = new ConcurrentHashMap<>(); // hashmap is fine for non-current methods
//        this.queue = new ConcurrentSkipListMap<>();
//        this.futures = new CopyOnWriteArrayList<>();
//        this.lock = new ReentrantLock();
//        this.taskScheduler = new ExecutorService[threads];
//        for (int i = 0; i < taskScheduler.length; i++) {
//            taskScheduler[i] = Executors.newSingleThreadExecutor();
//        }
//    }
//
//    @Override
//    public void start(String processId, long timestamp) {
//        taskScheduler[processId.hashCode() % taskScheduler.length].execute(() -> {
//            //final long now = System.currentTimeMillis();
//            final Process process = new Process(processId, timestamp);
//            processes.put(processId, process);
//            queue.put(timestamp, process);
//        });
//
//
//    }
//
//    // 1 execution of poll and lock is running at the same time (because update and delete is where queue or map is update
//    @Override
//    public void end(String processId) {
//        taskScheduler[processId.hashCode() % taskScheduler.length].execute(() -> {
//            lock.lock();
//            try {
//                final long now = System.currentTimeMillis();
//                processes.get(processId).setEndTime(System.currentTimeMillis());
//                // no need to update queue because map and queue have reference to same object
//                // memory leak when start is called for a process but the end method fails for the process, poll will never complete
//                // approach  to solve above :
//                if (!futures.isEmpty() && queue.firstEntry().getValue().getId() == processId) {
//
//                    pollNow();
//                    final var result = futures.remove(0);
//                    result.complete(null); // to give a notification that end is done and future is completed (push method)61->84
//
//
//                }
//            } finally {
//                lock.unlock();
//            }
//        });
//
//    }
//
//    @Override
//    public String poll() {
//        lock.lock(); //Head of line blocking until we get any completed future .
//        try {
//            final var result = new CompletableFuture<Void>();
//            if (!queue.isEmpty() && queue.firstEntry().getValue().getEndTime() != -1) {
//                pollNow();
//            } else {
//                // this future will be completed by the end()
//                futures.add(result);
//            }
//            try {
//                // get is a blocking method
//                result.get(10, TimeUnit.SECONDS);
//            } catch (ExecutionException | InterruptedException  | TimeoutException e) {
//                throw new RuntimeException(e);
//            }
//            return null;
//        } finally {
//            lock.unlock();
//        }
//
//    }
//
//    private String pollNow() {
//        final Process process = queue.firstEntry().getValue();
//
//        final var logStatement = process.getId() + "Started at " + process.getStartTime() + "and ended at " +
//                process.getEndTime();
//        System.out.println(logStatement);
//        processes.remove(process.getId());
//        queue.pollFirstEntry();
//        return logStatement;
//    }
//}
//
//package logger;


        import java.util.List;
        import java.util.Map;
        import java.util.concurrent.*;
        import java.util.concurrent.locks.Lock;
        import java.util.concurrent.locks.ReentrantLock;

public class LogClientImpl implements LogClient {
    private final ConcurrentSkipListMap<Long, List<Process>> queue;
    private final Map<String, Process> map;
    private final Lock lock;
    private final BlockingQueue<CompletableFuture<String>> pendingPolls;
    private final ExecutorService[] executorService;

    public LogClientImpl(int threads) {
        queue = new ConcurrentSkipListMap<>();
        map = new ConcurrentHashMap<>();
        lock = new ReentrantLock();
        pendingPolls = new LinkedBlockingQueue<>();
        executorService = new ExecutorService[threads];
        for (int i = 0; i < executorService.length; i++) {
            executorService[i] = Executors.newSingleThreadExecutor();
        }
    }

    public void start(final String taskId, long timestamp) {
        executorService[taskId.hashCode() % executorService.length].execute(() -> {
            final Process task = new Process(taskId, timestamp);
            map.put(taskId, task);
            queue.putIfAbsent(timestamp, new CopyOnWriteArrayList<>());
            queue.get(timestamp).add(task);
        });
    }

    public void end(final String taskId) {
        executorService[taskId.hashCode() % executorService.length].execute(() -> {
            map.get(taskId).setEndTime(System.currentTimeMillis());
            lock.lock();
            try {
                String result;
                while (!pendingPolls.isEmpty() && (result = pollNow()) != null) {
                    pendingPolls.take().complete(result);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        });
    }

    public String poll() {
        final CompletableFuture<String> result = new CompletableFuture<>();
        lock.lock();
        try {
            try {
                String logStatement;
                if (!pendingPolls.isEmpty()) {
                    pendingPolls.offer(result);
                } else if ((logStatement = pollNow()) != null) {
                    return logStatement;
                } else {
                    pendingPolls.offer(result);
                }
            } finally {
                lock.unlock();
            }
            return result.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private String pollNow() {
        if (!queue.isEmpty()) {
            for (final Process earliest : queue.firstEntry().getValue()) {
                if (earliest.getEndTime() != -1) {
                    queue.firstEntry().getValue().remove(earliest);
                    if (queue.firstEntry().getValue().isEmpty()) {
                        queue.pollFirstEntry();
                    }
                    map.remove(earliest.getId());
                    final var logStatement = "task " + earliest.getId() + " started at: " + earliest.getStartTime() + " and ended at: " + earliest.getEndTime();
                    System.out.println(logStatement);
                    return logStatement;
                }
            }
        }
        return null;
    }
}


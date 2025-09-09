#!/bin/bash

# Script to help debug intermittent subprocess failures locally
# Usage: ./debug-subprocess-loop.sh [iterations] [target]

ITERATIONS=${1:-50}
TARGET=${2:-"test.os.SubprocessTests"}
SCALA_VERSION="2.13.16"

echo "Running $TARGET in loop for $ITERATIONS iterations..."
echo "Set SUBPROCESS_STRESS_ITERATIONS env var to control stress test iterations"

SUCCESS_COUNT=0
FAILURE_COUNT=0

for i in $(seq 1 $ITERATIONS); do
    echo "=== Iteration $i/$ITERATIONS ==="
    
    if ./mill -i "os.jvm[$SCALA_VERSION].test.testOnly" "$TARGET" 2>&1; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        echo "✓ Iteration $i: SUCCESS"
    else
        FAILURE_COUNT=$((FAILURE_COUNT + 1))
        echo "✗ Iteration $i: FAILED"
        
        # Optionally stop on first failure
        if [ "$3" = "--stop-on-failure" ]; then
            echo "Stopping on first failure as requested"
            break
        fi
    fi
    
    # Small delay between runs
    sleep 0.1
done

echo ""
echo "=== SUMMARY ==="
echo "Total iterations: $ITERATIONS"
echo "Successes: $SUCCESS_COUNT"
echo "Failures: $FAILURE_COUNT"
echo "Success rate: $(( SUCCESS_COUNT * 100 / (SUCCESS_COUNT + FAILURE_COUNT) ))%"

if [ $FAILURE_COUNT -gt 0 ]; then
    echo ""
    echo "Failures detected! This may help reproduce the CI issue."
    exit 1
else
    echo ""
    echo "All tests passed. Try increasing iterations or running under stress."
    exit 0
fi


# Kill the process that is locking the file
Stop-Process -Id 48376 -Force

# Build the project again
./gradlew :app:processDebugResources

const express = require('express');
const { WebSocketServer } = require('ws');
const { spawn } = require('child_process');
const path = require('path');
const cors = require('cors');

const app = express();
app.use(cors());

// Serve a simple healthcheck route
app.get('/', (req, res) => {
    res.send({ status: 'Engine Backend is running' });
});

// Start the HTTP server
const PORT = process.env.PORT || 8080;
const server = app.listen(PORT, () => {
    console.log(`Backend server listening on port ${PORT}`);
});

// Attach WebSocket Server
const wss = new WebSocketServer({ server });

const enginePath = path.resolve(__dirname, '../engine-core/build/chaturanga-engine.jar');

wss.on('connection', (ws) => {
    console.log('Client connected. Spawning engine...');
    
    // Spawn a new engine process for this connection
    const engineProcess = spawn('java', ['-jar', enginePath]);
    
    // Send engine output to the client
    engineProcess.stdout.on('data', (data) => {
        const lines = data.toString().split('\n');
        for (const line of lines) {
            const trimmed = line.trim();
            if (trimmed) {
                console.log(`[Engine -> Client] ${trimmed}`);
                ws.send(trimmed);
            }
        }
    });
    
    engineProcess.stderr.on('data', (data) => {
        console.error(`[Engine Error] ${data.toString()}`);
    });
    
    // Receive commands from the client and send to engine
    ws.on('message', (message) => {
        const command = message.toString().trim();
        console.log(`[Client -> Engine] ${command}`);
        engineProcess.stdin.write(`${command}\n`);
    });
    
    ws.on('close', () => {
        console.log('Client disconnected. Killing engine...');
        engineProcess.kill();
    });
    
    engineProcess.on('exit', (code) => {
        console.log(`Engine process exited with code ${code}`);
        ws.close();
    });
});

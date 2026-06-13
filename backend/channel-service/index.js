const express = require('express');
const axios   = require('axios');
const http    = require('http');

const app = express();
app.use(express.json());

const CRM_RECEIPT_URL = process.env.CRM_RECEIPT_URL || 'http://localhost:8087/api/receipt';
const PORT = process.env.PORT || 9090;

// Shorter delays keep demos responsive; long delays pile up across launches.
const DELIVERY_DELAY_MS = [400, 1500];
const OPEN_DELAY_MS     = [600, 2000];
const CLICK_DELAY_MS    = [400, 1200];

// Cap parallel simulations so callbacks do not flood the CRM after the first launch.
const MAX_CONCURRENT = Number(process.env.MAX_CONCURRENT || 6);

const OUTCOMES = [
    { status: 'delivered', weight: 85 },
    { status: 'failed',    weight: 15 },
];

const ENGAGEMENT = [
    { status: 'opened',  weight: 60 },
    { status: 'skipped', weight: 40 },
];

const CLICK = [
    { status: 'clicked', weight: 25 },
    { status: 'skipped', weight: 75 },
];

const httpAgent = new http.Agent({ keepAlive: true, maxSockets: MAX_CONCURRENT });
const api = axios.create({
    timeout: 10000,
    httpAgent,
});

let activeSimulations = 0;
const pendingJobs = [];

function weightedRandom(outcomes) {
    const total = outcomes.reduce((sum, o) => sum + o.weight, 0);
    let rand = Math.random() * total;
    for (const outcome of outcomes) {
        rand -= outcome.weight;
        if (rand <= 0) return outcome.status;
    }
    return outcomes[outcomes.length - 1].status;
}

function randomDelay([minMs, maxMs]) {
    return Math.floor(Math.random() * (maxMs - minMs + 1)) + minMs;
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function sendCallback(logId, status, attempt = 1) {
    try {
        await api.post(CRM_RECEIPT_URL, { logId, status });
        console.log(`[callback] logId=${logId} status=${status}`);
    } catch (err) {
        console.error(`[callback-error] logId=${logId} status=${status} attempt=${attempt}:`, err.message);
        if (attempt < 3) {
            await sleep(1000 * attempt);
            return sendCallback(logId, status, attempt + 1);
        }
    }
}

async function simulateDelivery(logId, channel) {
    await sleep(randomDelay(DELIVERY_DELAY_MS));

    const deliveryStatus = weightedRandom(OUTCOMES);
    await sendCallback(logId, deliveryStatus);

    if (deliveryStatus === 'failed') return;

    await sleep(randomDelay(OPEN_DELAY_MS));
    const engagementStatus = weightedRandom(ENGAGEMENT);
    if (engagementStatus === 'skipped') return;

    await sendCallback(logId, 'opened');

    await sleep(randomDelay(CLICK_DELAY_MS));
    const clickStatus = weightedRandom(CLICK);
    if (clickStatus === 'skipped') return;

    await sendCallback(logId, 'clicked');
}

function drainQueue() {
    while (activeSimulations < MAX_CONCURRENT && pendingJobs.length > 0) {
        const { logId, channel } = pendingJobs.shift();
        activeSimulations += 1;

        simulateDelivery(logId, channel)
            .catch(err => {
                console.error(`[simulate-error] logId=${logId}:`, err.message);
            })
            .finally(() => {
                activeSimulations -= 1;
                drainQueue();
            });
    }
}

function enqueueSimulation(logId, channel) {
    pendingJobs.push({ logId, channel });
    drainQueue();
}

app.post('/send', (req, res) => {
    const { logId, recipient, message, channel } = req.body;

    if (!logId || !recipient || !message) {
        return res.status(400).json({ error: 'Missing required fields' });
    }

    console.log(
        `[send] logId=${logId} channel=${channel} recipient=${recipient} ` +
        `(active=${activeSimulations}, queued=${pendingJobs.length})`
    );

    res.status(202).json({ message: 'Accepted', logId });
    enqueueSimulation(logId, channel);
});

app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        service: 'channel-service',
        activeSimulations,
        queuedSimulations: pendingJobs.length,
        maxConcurrent: MAX_CONCURRENT,
    });
});

app.listen(PORT, () => {
    console.log(`Channel service running on port ${PORT}`);
    console.log(`CRM receipt URL: ${CRM_RECEIPT_URL}`);
    console.log(`Max concurrent simulations: ${MAX_CONCURRENT}`);
});

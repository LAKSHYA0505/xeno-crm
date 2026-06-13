const express = require('express');
const axios   = require('axios');

const app  = express();
app.use(express.json());

const CRM_RECEIPT_URL = 'http://localhost:8087/api/receipt';

// Outcome probabilities
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

function weightedRandom(outcomes) {
    const total  = outcomes.reduce((sum, o) => sum + o.weight, 0);
    let   rand   = Math.random() * total;
    for (const outcome of outcomes) {
        rand -= outcome.weight;
        if (rand <= 0) return outcome.status;
    }
    return outcomes[outcomes.length - 1].status;
}

function randomDelay(minMs, maxMs) {
    return Math.floor(Math.random() * (maxMs - minMs + 1)) + minMs;
}

async function sendCallback(logId, status) {
    try {
        await axios.post(CRM_RECEIPT_URL, { logId, status });
        console.log(`[callback] logId=${logId} status=${status}`);
    } catch (err) {
        console.error(`[callback-error] logId=${logId} status=${status}:`, err.message);
        // Retry once after 3 seconds
        setTimeout(async () => {
            try {
                await axios.post(CRM_RECEIPT_URL, { logId, status });
                console.log(`[callback-retry-ok] logId=${logId} status=${status}`);
            } catch (retryErr) {
                console.error(`[callback-retry-fail] logId=${logId}:`, retryErr.message);
            }
        }, 3000);
    }
}

async function simulateDelivery(logId, channel) {
    // Step 1 — simulate network delay before delivery outcome
    await new Promise(r => setTimeout(r, randomDelay(1000, 5000)));

    const deliveryStatus = weightedRandom(OUTCOMES);
    await sendCallback(logId, deliveryStatus);

    if (deliveryStatus === 'failed') return;

    // Step 2 — simulate open (only if delivered)
    await new Promise(r => setTimeout(r, randomDelay(3000, 10000)));
    const engagementStatus = weightedRandom(ENGAGEMENT);
    if (engagementStatus === 'skipped') return;

    await sendCallback(logId, 'opened');

    // Step 3 — simulate click (only if opened)
    await new Promise(r => setTimeout(r, randomDelay(2000, 8000)));
    const clickStatus = weightedRandom(CLICK);
    if (clickStatus === 'skipped') return;

    await sendCallback(logId, 'clicked');
}

// POST /send — called by CRM for each message
app.post('/send', (req, res) => {
    const { logId, recipient, message, channel } = req.body;

    if (!logId || !recipient || !message) {
        return res.status(400).json({ error: 'Missing required fields' });
    }

    console.log(`[send] logId=${logId} channel=${channel} recipient=${recipient}`);

    // Respond immediately — async simulation happens in background
    res.status(202).json({ message: 'Accepted', logId });

    // Fire simulation asynchronously
    simulateDelivery(logId, channel).catch(err => {
        console.error(`[simulate-error] logId=${logId}:`, err.message);
    });
});

// Health check
app.get('/health', (req, res) => {
    res.json({ status: 'ok', service: 'channel-service' });
});

const PORT = 9090;
app.listen(PORT, () => {
    console.log(`Channel service running on port ${PORT}`);
});
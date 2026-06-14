# SoleStreet CRM Intelligence: Frontend Dashboard

Welcome to the frontend dashboard repository of SoleStreet CRM Intelligence — an AI-native, high-performance campaign launchpad designed for modern D2C shoe brands to intelligently segment, personalize, and reach their shoppers.

Built with TypeScript, React, Vite, and Tailwind CSS, this frontend offers a seamless user experience that guides marketers through the natural language campaigning loop, featuring interactive status funnels and real-time performance graphs.

## ⚡ The AI-Native Marketing Loop

SoleStreet integrates AI natively into the actual workflow of launching campaigns, avoiding the “bolted-on” cosmetic approach.

```text
[1. Describe Goal] ➔ Marketer types: "high-spending running-shoe buyers in Delhi"
                           │
                           ▼ (AI Segment Parser)
[2. Review Sample] ➔ View matched profiles, JSON rules, and AI copy draft
                           │
                           ▼ (Conversational Message Refiner)
[3. Conversational] ➔ Type instructions (e.g., "add discount") to edit in place
                           │
                           ▼ (Async Sending Engine)
[4. Live Statistics] ➔ Watch live funnel updates and click "Generate Analysis"
                           │
                           ▼ (AI Performance Analyst)
[5. Next Step Recs] ➔ View AI recommendations and launch follow-up dispatches
```

## 🎨 Key Features & Functional Components

### 1. Conversational Segment Parsing (AI Stage 1)

Marketers write standard English target intents (e.g. "female customers who spent over 5000 in Mumbai"). The interface displays an animated AI thinking state, sends the query to our backend parser, and returns the segment count, sample matching customer profiles, and compiled database conditions.

### 2. Conversational Message Refiner (AI Stage 2)

In the template review step, marketers can type plain-English adjustments like "make it more urgent" or "add a 10% discount". The AI rewrites the copy in-place while safeguarding active dynamic personalization tokens (`{{name}}` and `{{last_product}}`).

### 3. Real-Time Funnel Reporting (Stage 4)

When a campaign is launched, the screen transitions to our Live Stats panel. It polls the server every 4 seconds to pull aggregate carrier states, displaying conversion stats across six columns: Audience, Sent, Delivered, Opened, Clicked, and Orders.

### 4. Interactive Funnel Visualizations (Recharts)

Clicking "Generate Analysis" compiles your campaign's data and displays:

- An AI Campaign Analysis detailing conversion rates and the exact revenue impact.
- An interactive Conversion Funnel Bar Chart mapping progression rates.
- An Outcome Breakdown Donut Chart showing engagement trends.

### 5. AI-Recommended Follow-Up Campaigns (Campaign Escalation)

Once a campaign completes, the AI evaluates performance, drafts a fresh offer, escalates the channel (e.g., WhatsApp ➔ SMS), and offers a "Launch follow-up" action targeting exactly those customers who did not open the previous message.

## 🛠️ Tech Stack & Build Optimization

- **React v18 & TypeScript:** Ensures complete type-safety across API models.
- **Vite v8:** Leverages rapid hot-module reloading (HMR) and fast build compilations.
- **Tailwind CSS v4:** Utility-first CSS compiling premium, custom component designs.
- **Recharts:** High-performance SVG rendering for smooth, responsive animations.
- **Lucide React:** Clean, lightweight visual icons.

### Production Build Optimization

Vite utilizes LightningCSS by default, which can throw warnings on Tailwind v4 CSS-native custom directives like `@theme` or `@utility`. To resolve this, our configuration forces Vite to compile using Esbuild for stylesheet compression, delivering a warning-free build:

```ts
// vite.config.ts
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    cssMinify: "esbuild",
    minify: "esbuild",
  },
});
```

## 🚀 Installation & Local Launch Guidelines

### Prerequisites

- Node.js v18+
- npm or yarn

### Setup Steps

1. Clone the repository and navigate to the `frontend` directory.
2. Install dependencies:

```bash
npm install
```

3. Start the Vite local development server on port 5173:

```bash
npm run dev
```

4. To test a production build:

```bash
npm run build
npm run preview
```

> Note: Ensure your backend Spring Boot CRM is running on port `8087` so the API client can route requests to `/api/segments` and `/api/campaigns` successfully.

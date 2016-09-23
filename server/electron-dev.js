const {app, BrowserWindow} = require('electron')
let w;
app.on('ready', function() {
    w = new BrowserWindow({"web-preferences": {"web-security": false}});
    w.loadURL(`http://localhost:3449/`);
    w.webContents.openDevTools();
    w.on('closed', () => w = null);
});
app.on('window-all-closed', () => app.quit());

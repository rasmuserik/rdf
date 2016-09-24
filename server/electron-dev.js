const {app, BrowserWindow} = require('electron')
let w;
app.on('ready', function() {
    w = new BrowserWindow({"web-preferences": {"web-security": false}});
    w.loadURL(`http://localhost:3449/`);
    /*
    w.webContents.executeJavaScript(`
        console.log(require('module').globalPaths);
        require('module').globalPaths.push(process.cwd() + '/node_modules');

        `);
        */
    w.webContents.openDevTools();
    w.on('closed', () => w = null);
});
app.on('window-all-closed', () => app.quit());

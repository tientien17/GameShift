package com.gameshift.app.shizuku;

interface IUserShellService {
    String exec(String command);
    void destroy();
}

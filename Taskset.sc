Taskset {
	classvar <>core = 1;

	*initClass {
		Class.initClassTree(Server);
		Class.initClassTree(ServerBoot);
		ServerBoot.add({ this.setServerCPU }, \default);
	}



	*setServerCPU { |server(Server.default), argCore|
		if (core.notNil) {
			"taskset -cp % %".format(argCore ? this.core, server.pid).unixCmd
		} {
			"taskset -cp %".format(server.pid).unixCmd
		}
	}
}
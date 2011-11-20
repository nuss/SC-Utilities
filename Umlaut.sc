//redFrik

//updated 090913 - thanks thor for fix4

//--todo:
//need to find ascii for vthorn, vThorn, veth, vEth, vyacute, vYacute

Umlaut {
	classvar varing, vAring, vauml, vAuml, vouml, vOuml, vuuml, vUuml, veacute, vEacute,
	vegrave, vEgrave, vecirc, vEcirc, vagrave, vAgrave, vicirc, vIcirc, viuml, vIuml, veuml, vEuml,
	vccedil, vCcedil, vszlig, vaelig, vAelig, vatilde, vAtilde, voslash, vOslash, vntilde, vNtilde,
	vthorn, vThorn, veth, vEth, vaacute, vAacute, viacute, vIacute, vuacute, vUacute,
	voacute, vOacute, vyacute, vYacute;
	
	*initClass {
		var esc= 195.asAscii.asString;
		
		varing= esc++165.asAscii;
		vAring= esc++133.asAscii;
		vauml= esc++164.asAscii;
		vAuml= esc++132.asAscii;
		vouml= esc++182.asAscii;
		vOuml= esc++150.asAscii;
		vuuml= esc++188.asAscii;
		vUuml= esc++156.asAscii;
		veacute= esc++169.asAscii;
		vEacute= esc++137.asAscii;
		
		vegrave= esc++168.asAscii;
		vEgrave= esc++136.asAscii;
		vecirc= esc++170.asAscii;
		vEcirc= esc++138.asAscii;
		vagrave= esc++160.asAscii;
		vAgrave= esc++128.asAscii;
		vicirc= esc++174.asAscii;
		vIcirc= esc++142.asAscii;
		viuml= esc++175.asAscii;
		vIuml= esc++143.asAscii;
		veuml= esc++171.asAscii;
		vEuml= esc++139.asAscii;
		
		vccedil= esc++167.asAscii;
		vCcedil= esc++135.asAscii;
		vszlig= esc++159.asAscii;
		vaelig= esc++166.asAscii;
		vAelig= esc++134.asAscii;
		vatilde= esc++163.asAscii;
		vAtilde= esc++131.asAscii;
		voslash= esc++184.asAscii;
		vOslash= esc++152.asAscii;
		vntilde= esc++177.asAscii;
		vNtilde= esc++145.asAscii;
		
		vthorn= esc++190.asAscii;
		vThorn= esc++158.asAscii;
		veth= esc++176.asAscii;
		vEth= esc++144.asAscii;
		vaacute= esc++161.asAscii;
		vAacute= esc++129.asAscii;
		viacute= esc++173.asAscii;
		vIacute= esc++141.asAscii;
		vuacute= esc++186.asAscii;
		vUacute= esc++154.asAscii;
		voacute= esc++179.asAscii;
		vOacute= esc++147.asAscii;
		vyacute= esc++189.asAscii;
		vYacute= esc++157.asAscii;
	}
	
	*fix {|str|
		^str
			.replace(varing, 140.asAscii)			//�
			.replace(vAring, 129.asAscii)			//�
			.replace(vauml, 138.asAscii)			//�
			.replace(vAuml, 128.asAscii)			//�
			.replace(vouml, 154.asAscii)			//�
			.replace(vOuml, 133.asAscii)			//�
			.replace(vuuml, 159.asAscii)			//�
			.replace(vUuml, 134.asAscii)			//�
			.replace(veacute, 142.asAscii)			//�
			.replace(vEacute, 131.asAscii)			//�
	}
	*fix2 {|str|
		^str
			.replace(vegrave, 143.asAscii)			//�
			.replace(vEgrave, 233.asAscii)			//�
			.replace(vecirc, 144.asAscii)			//�
			.replace(vEcirc, 230.asAscii)			//�
			.replace(vagrave, 136.asAscii)			//�
			.replace(vAgrave, 203.asAscii)			//�
			.replace(vicirc, 148.asAscii)			//�
			.replace(vIcirc, 235.asAscii)			//�
			.replace(viuml, 149.asAscii)			//�
			.replace(vIuml, 236.asAscii)			//�
			.replace(veuml, 145.asAscii)			//�
			.replace(vEuml, 232.asAscii)			//�
	}
	*fix3 {|str|
		^str
			.replace(vccedil, 141.asAscii)			//�
			.replace(vCcedil, 130.asAscii)			//�
			.replace(vaelig, 190.asAscii)			//�
			.replace(vAelig, 174.asAscii)			//�
			.replace(vatilde, 139.asAscii)			//�
			.replace(vAtilde, 204.asAscii)			//�
			.replace(voslash, 191.asAscii)			//�
			.replace(vOslash, 175.asAscii)			//�
			.replace(vntilde, 150.asAscii)			//�
			.replace(vNtilde, 132.asAscii)			//�
			.replace(vszlig, 167.asAscii)			//�
	}
	*fix4 {|str|
		^str
			//.replace(vthorn, ???.asAscii)			//
			//.replace(vThorn, ???.asAscii)			//
			//.replace(veth, ???.asAscii)			//
			//.replace(vEth, ???.asAscii)			//
			.replace(vaacute, 135.asAscii)			//
			.replace(vAacute, 231.asAscii)			//
			.replace(viacute, 146.asAscii)			//
			.replace(vIacute, 234.asAscii)			//
			.replace(vuacute, 156.asAscii)			//
			.replace(vUacute, 242.asAscii)			//
			.replace(voacute, 151.asAscii)			//
			.replace(vOacute, 238.asAscii)			//
			//.replace(vyacute, ???.asAscii)			//
			//.replace(vYacute, ???.asAscii)			//
	}
	*fixAll {|str|
		^this.fix4(this.fix3(this.fix2(this.fix(str))))
	}
}


/*
http://www.cs.umu.se/~mr/ISOlatin1.html
"�".do{|x| x.ascii.postln};""
1000.do{|x| [x, (x).asAscii].postln};""
*/

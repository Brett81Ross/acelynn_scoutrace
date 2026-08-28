/* Acelynn's ScoutTrace™ — Demo/Help shell wrapper */
module.exports=async function handler(req,res){
  if(req.method!=='GET'&&req.method!=='HEAD'){res.statusCode=405;res.setHeader('Allow','GET, HEAD');return res.end('Method Not Allowed')}
  try{
    const proto=String(req.headers['x-forwarded-proto']||'https').split(',')[0].trim(),host=req.headers.host;if(!host)throw new Error('Missing host');
    const upstream=await fetch(`${proto}://${host}/raw-index.html`,{headers:{'user-agent':req.headers['user-agent']||'ScoutTraceDemoShell/1.0','accept':'text/html,*/*'}});if(!upstream.ok)throw new Error(`Base page returned ${upstream.status}`);
    let html=await upstream.text();
    html=html.replace(/v1\.2\.0/g,'v1.2.1').replace(/Version 1\.2\.0/g,'Version 1.2.1').replace("const V='1.2.0'","const V='1.2.1'");
    const scripts='<script src="/demo-config.js" defer></script><script src="/cactusbyte-demo.js" defer></script>';
    if(!html.includes('/cactusbyte-demo.js'))html=html.includes('</body>')?html.replace('</body>',scripts+'</body>'):html+scripts;
    res.statusCode=200;res.setHeader('Content-Type','text/html; charset=utf-8');res.setHeader('Cache-Control','public, s-maxage=60, stale-while-revalidate=300');if(req.method==='HEAD')return res.end();return res.end(html)
  }catch(error){console.error('ScoutTrace demo shell failed:',error);res.statusCode=502;res.setHeader('Content-Type','text/plain; charset=utf-8');return res.end('ScoutTrace is temporarily unavailable. Please try again.')}
};

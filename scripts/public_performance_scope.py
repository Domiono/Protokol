"""Skip unrelated API/admin/data writes when a commit changes only public speed files."""
import subprocess
SAFE={
 '.github/workflows/public-speed-audit.yml','.github/workflows/deploy-rentmor.yml',
 '.github/workflows/deploy-gallery.yml','.github/workflows/deploy-rentmor-images.yml',
 'scripts/summarize_speed.py','scripts/patch_static_cards.py','scripts/test_static_cards.py',
 'scripts/png_metadata.py','scripts/prepare_rentmor_images.py','scripts/verify_fast_gallery.py',
 'scripts/check_public_image_markup.py','scripts/public_performance_scope.py',
 'docs/PERFORMANCE-2026-10-26.md',
}
p=subprocess.run(['git','diff','--name-only','HEAD^','HEAD'],capture_output=True,text=True)
files=set(p.stdout.splitlines())
print('public_only='+str(p.returncode==0 and bool(files) and files<=SAFE).lower())

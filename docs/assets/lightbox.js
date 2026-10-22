// Lightbox functionality for documentation screenshots
(function() {
  'use strict';

  // Create lightbox DOM structure
  const createLightbox = () => {
    const lightbox = document.createElement('div');
    lightbox.className = 'lightbox';
    lightbox.id = 'screenshot-lightbox';
    lightbox.innerHTML = `
      <div class="lightbox-content">
        <img id="lightbox-image" src="" alt="" />
        <button class="lightbox-close" aria-label="Fermer">&times;</button>
      </div>
    `;
    document.body.appendChild(lightbox);
    return lightbox;
  };

  // Initialize lightbox
  const init = () => {
    let lightbox = document.getElementById('screenshot-lightbox');
    if (!lightbox) {
      lightbox = createLightbox();
    }

    const lightboxImage = lightbox.querySelector('#lightbox-image');
    const closeButton = lightbox.querySelector('.lightbox-close');

    // Add click handlers to all screenshot images
    const screenshots = document.querySelectorAll('img.shot');
    screenshots.forEach(img => {
      img.addEventListener('click', (e) => {
        e.stopPropagation();
        lightboxImage.src = img.src;
        lightboxImage.alt = img.alt;
        lightbox.classList.add('active');
      });
    });

    // Close on × button
    closeButton.addEventListener('click', () => {
      lightbox.classList.remove('active');
    });

    // Close on background click
    lightbox.addEventListener('click', (e) => {
      if (e.target === lightbox) {
        lightbox.classList.remove('active');
      }
    });

    // Close on Escape key
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && lightbox.classList.contains('active')) {
        lightbox.classList.remove('active');
      }
    });
  };

  // Initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
